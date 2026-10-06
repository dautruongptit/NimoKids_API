package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.config.TimeConfig;
import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.SessionStatus;
import com.nimokids.mapper.GameMapper;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.OptionGenerator;
import com.nimokids.service.generation.DistractorRule;
import com.nimokids.service.generation.DistractorRules;
import com.nimokids.service.generation.TagMatcher;
import com.nimokids.service.impl.ActivityLogServiceImpl;
import com.nimokids.service.impl.GameSessionServiceImpl;
import com.nimokids.service.impl.OptionGeneratorImpl;
import com.nimokids.service.impl.PlayerServiceImpl;
import com.nimokids.service.impl.StickerServiceImpl;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The whole engine on a REAL PostgreSQL with the dev seed: the real option generator, the real services and the real
 * queries. Opt-in (DB_HOST). Everything is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GameSessionServiceImpl.class, PlayerServiceImpl.class, StickerServiceImpl.class, ActivityLogServiceImpl.class,
        OptionGeneratorImpl.class, GameMapper.class, TimeConfig.class, JacksonAutoConfiguration.class})
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class EngineIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private GameSessionService gameSessionService;
    @Autowired private OptionGenerator optionGenerator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TopicRepository topicRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private GameQuestionRepository questionRepository;
    @Autowired private AnswerItemRepository answerItemRepository;
    @Autowired private GameSessionRepository sessionRepository;
    @Autowired private PlayerStickerRepository playerStickerRepository;

    // -------------------------------------------------------------------- seed

    private static final List<String> ROOTS = List.of("ANIMALS", "FRUITS", "VEHICLES", "COLORS", "SHAPES", "NUMBERS",
            "ALPHABET", "FOOD", "TOYS", "CLOTHES", "HOME", "NATURE", "SEA_ANIMALS", "FARM_ANIMALS");

    private List<GameQuestion> seededQuestions() {
        return em.createQuery("select q from GameQuestion q where q.active = true and q.topic.code in :codes",
                GameQuestion.class).setParameter("codes", ROOTS).getResultList();
    }

    @Test
    void theSeedHasFourteenPlayableRootTopicsWithTenQuestionsEach() {
        List<UUID> playable = questionRepository.findPlayableTopicIds(5);
        List<GameQuestion> all = seededQuestions();
        for (String code : ROOTS) {
            Topic topic = topicRepository.findByCode(code).orElseThrow();
            assertThat(topic.getParentTopic()).as(code).isNull();
            assertThat(playable).as(code).contains(topic.getId());
            assertThat(all.stream().filter(q -> q.getTopic().getCode().equals(code))).as(code).hasSize(10);
        }
        assertThat(all).hasSize(140);
    }

    @Test
    void aRootTopicDrawsQuestionsFromAllItsDescendants() {
        Topic sea = topicRepository.findByCode("SEA_ANIMALS").orElseThrow();
        Topic farm = topicRepository.findByCode("FARM_ANIMALS").orElseThrow();
        Topic parent = topicRepository.saveAndFlush(Topic.builder().code("IT_PARENT").name("Parent").slug("it-parent")
                .minAge((short) 1).maxAge((short) 5).build());
        sea.setParentTopic(parent);
        farm.setParentTopic(parent);
        topicRepository.flush();

        List<UUID> subtree = topicRepository.findActiveSubtreeIds(parent.getId());
        assertThat(subtree).containsExactlyInAnyOrder(parent.getId(), sea.getId(), farm.getId());
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();
        // 10 questions of each child and none on the parent itself.
        assertThat(questionRepository.findCandidateIds(subtree, mode.getId(), 3)).hasSize(20);
    }

    @Test
    void theOldStaticSeedWasReplacedAndNoLegacyRowsRemain() {
        Long legacyItems = (Long) em.createNativeQuery("select count(*) from answer_items where code like 'LEGACY%'").getSingleResult();
        Long staticTable = (Long) em.createNativeQuery("select count(*) from information_schema.tables where table_name = 'question_options'").getSingleResult();

        assertThat(legacyItems).isZero();
        assertThat(staticTable).isZero();
    }

    @Test
    void everySeededQuestionAlwaysProducesFourDistinctOptionsFromItsOwnGroup() {
        List<GameQuestion> questions = seededQuestions();
        assertThat(questions).hasSize(140);

        for (GameQuestion question : questions) {
            String category = question.getTopic().getCode().toLowerCase();
            String label = question.getQuestionText() + " -> " + question.getCorrectAnswerItem().getName();
            for (int run = 0; run < 10; run++) {
                List<SnapshotOption> options = optionGenerator.generate(question);

                assertThat(options).as(label).hasSize(4);
                assertThat(options.stream().map(SnapshotOption::text).collect(Collectors.toSet())).as(label).hasSize(4);
                assertThat(options.stream().filter(SnapshotOption::isCorrect)).as(label).hasSize(1);
                assertThat(options.stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow().answerItemId())
                        .as(label).isEqualTo(question.getCorrectAnswerItem().getId());
                for (SnapshotOption option : options) {
                    assertThat(answerItemRepository.findById(option.answerItemId()).orElseThrow().getMetadata().get("category"))
                            .as(label).isEqualTo(category);
                }
            }
        }
    }

    @Test
    void noSeededQuestionCanEverShowASecondRightAnswer() {
        List<AnswerItem> items = answerItemRepository.findAll();

        for (GameQuestion question : seededQuestions()) {
            String label = question.getQuestionText() + " -> " + question.getCorrectAnswerItem().getName();
            Map<String, Object> correctMatch = DistractorRules.parseCorrectMatch(question.getMetadata());

            // The stated condition really describes the correct answer.
            assertThat(TagMatcher.satisfies(question.getCorrectAnswerItem().getMetadata(), correctMatch)).as(label).isTrue();

            // Every pool keeps enough SAFE wrong answers (items that do not also satisfy the condition) to fill its slots.
            for (DistractorRule rule : DistractorRules.parse(question.getMetadata(), 3)) {
                long safe = items.stream()
                        .filter(AnswerItem::isActive)
                        .filter(item -> !item.getId().equals(question.getCorrectAnswerItem().getId()))
                        .filter(item -> item.getMetadata().entrySet().containsAll(rule.match().entrySet()))
                        .filter(item -> !TagMatcher.satisfies(item.getMetadata(), correctMatch))
                        .count();
                assertThat(safe).as(label + " / safe wrong answers in pool " + rule.match()).isGreaterThanOrEqualTo(rule.count());
            }

            // Generated options: exactly one option satisfies the condition, and it is the correct one.
            for (int run = 0; run < 20; run++) {
                long satisfying = optionGenerator.generate(question).stream().filter(option -> TagMatcher.satisfies(
                        answerItemRepository.findById(option.answerItemId()).orElseThrow().getMetadata(), correctMatch)).count();
                assertThat(satisfying).as(label).isEqualTo(1);
            }
        }
    }

    @Test
    void lookAlikeAnswersAreNeverOfferedAsWrongAnswers() {
        GameQuestion eggs = em.createQuery("select q from GameQuestion q where q.questionText = 'Which animal lays eggs and has feathers?'",
                GameQuestion.class).getSingleResult();
        GameQuestion sky = em.createQuery("select q from GameQuestion q where q.questionText = 'What color is the sky on a clear day?'",
                GameQuestion.class).getSingleResult();
        for (int run = 0; run < 50; run++) {
            assertThat(optionGenerator.generate(eggs)).extracting(SnapshotOption::text).doesNotContain("Duck");
            assertThat(optionGenerator.generate(sky)).extracting(SnapshotOption::text).doesNotContain("Dark Blue");
        }
    }

    // ------------------------------------------------------------- full engine

    @Test
    void aWholeGameOnTheSeedPlaysThroughTheRealServices() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Topic animals = topicRepository.findByCode("ANIMALS").orElseThrow();
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();

        GameSessionResponse started = gameSessionService.createSession(
                anonymousId, new CreateGameSessionRequest(animals.getId(), mode.getId(), null));
        em.flush();
        em.clear();

        // The payload sent to the child carries 4 options and no correctness information at all.
        assertThat(started.question().options()).hasSize(4);
        assertThat(objectMapper.writeValueAsString(started)).doesNotContainIgnoringCase("correct");
        assertThat(started.timeLimitSeconds()).isEqualTo(8);

        GameSession session = sessionRepository.findBySessionId(started.sessionId()).orElseThrow();
        List<SessionQuestion> questions = session.getSessionQuestions();
        assertThat(questions).hasSize(5);

        // The subtree of a root always includes the root itself.
        Set<UUID> allowedTopics = new HashSet<>(topicRepository.findActiveSubtreeIds(animals.getId()));
        Set<UUID> questionIds = new HashSet<>();
        for (SessionQuestion sq : questions) {
            assertThat(allowedTopics).contains(sq.getQuestion().getTopic().getId());
            questionIds.add(sq.getQuestion().getId());
            assertThat(sq.getOptionsSnapshot()).hasSize(4);
            assertThat(sq.getOptionsSnapshot().stream().filter(SnapshotOption::isCorrect)).hasSize(1);
        }
        assertThat(questionIds).hasSize(5);

        // Play: right answers for the first 3 questions, wrong ones for the last 2. The server grades from the snapshot.
        AnswerResponse last = null;
        for (int i = 0; i < 5; i++) {
            SessionQuestion sq = questions.get(i);
            SnapshotOption pick = sq.getOptionsSnapshot().stream()
                    .filter(option -> option.isCorrect() == (questions.indexOf(sq) < 3))
                    .findFirst().orElseThrow();
            last = gameSessionService.submitAnswer(anonymousId, session.getSessionId(),
                    new SubmitAnswerRequest(sq.getQuestion().getId(), pick.optionId()));

            assertThat(last.correct()).isEqualTo(i < 3);
            assertThat(last.correctAnswer().id()).isEqualTo(
                    sq.getOptionsSnapshot().stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow().optionId());
            if (i < 4) {
                assertThat(last.nextQuestion().question().id()).isEqualTo(questions.get(i + 1).getQuestion().getId());
                assertThat(objectMapper.writeValueAsString(last.nextQuestion())).doesNotContainIgnoringCase("correct");
            } else {
                assertThat(last.nextQuestion()).isNull();
            }
        }
        em.flush();
        em.clear();

        GameResultResponse result = gameSessionService.getResult(anonymousId, session.getSessionId());
        assertThat(result.score()).isEqualTo(3);
        assertThat(result.correctAnswers()).isEqualTo(3);
        assertThat(result.wrongAnswers()).isEqualTo(2);
        assertThat(result.accuracy()).isEqualTo(60.0);
        assertThat(result.maxStreak()).isEqualTo(3);
        assertThat(result.earnedStickers()).extracting(s -> s.code()).contains("FIRST_GAME");

        GameSession finished = sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        // The evidence is permanent: the stored snapshot still matches what the child was shown.
        assertThat(finished.getSessionQuestions().get(0).getOptionsSnapshot())
                .extracting(SnapshotOption::optionId)
                .containsExactlyInAnyOrderElementsOf(
                        questions.get(0).getOptionsSnapshot().stream().map(SnapshotOption::optionId).toList());
        assertThat(finished.getSessionQuestions().get(0).getSelectedOptionId()).isNotNull();
        assertThat(Map.of("fly", playerStickerRepository.findBySessionId(finished.getId()).size())).containsEntry("fly", 1);
    }

    @Test
    void aLeafTopicOnlyAsksItsOwnQuestions() {
        UUID anonymousId = UUID.randomUUID();
        Topic fruits = topicRepository.findByCode("FRUITS").orElseThrow();
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();

        GameSessionResponse started = gameSessionService.createSession(
                anonymousId, new CreateGameSessionRequest(fruits.getId(), mode.getId(), null));
        em.flush();
        em.clear();

        GameSession session = sessionRepository.findBySessionId(started.sessionId()).orElseThrow();
        assertThat(session.getSessionQuestions()).allSatisfy(
                sq -> assertThat(sq.getQuestion().getTopic().getCode()).isEqualTo("FRUITS"));
    }
}
