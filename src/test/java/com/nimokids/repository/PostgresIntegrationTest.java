package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.QuestionOption;
import com.nimokids.entity.Sticker;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.AssetType;
import com.nimokids.entity.enums.SessionStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Runs against a REAL PostgreSQL that already has the Flyway schema. Opt-in: only runs when the DB_HOST
 * environment variable is set (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD are read by application-dev.yml).
 * Every test runs in a transaction that is rolled back, so no data is left behind.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class PostgresIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private TopicRepository topicRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private GameQuestionRepository questionRepository;
    @Autowired private AnonymousPlayerRepository playerRepository;
    @Autowired private GameSessionRepository sessionRepository;
    @Autowired private StickerRepository stickerRepository;
    @Autowired private PlayerStickerRepository playerStickerRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;

    @Test
    void playableQuestionQueriesApplyEveryEligibilityRule() {
        Topic topic = newTopic();
        GameMode mode = newMode();
        Set<UUID> playable = Set.of(
                question(topic, mode, 4, 1, true, 1, 5).getId(),
                question(topic, mode, 4, 1, true, 1, 5).getId(),
                question(topic, mode, 2, 1, true, 1, 5).getId(),
                question(topic, mode, 4, 1, true, 1, 5).getId(),
                question(topic, mode, 4, 1, true, 1, 5).getId());
        question(topic, mode, 4, 1, false, 1, 5);   // inactive question
        question(topic, mode, 1, 1, true, 1, 5);    // only one option
        question(topic, mode, 4, 0, true, 1, 5);    // no correct option
        GameQuestion older = question(topic, mode, 4, 1, true, 4, 5); // ages 4-5 only
        em.flush();

        List<UUID> noAgeFilter = questionRepository.findPlayableIds(topic.getId(), mode.getId(), 0, "NO_SUCH_MODE");
        assertThat(Set.copyOf(noAgeFilter)).containsAll(playable).contains(older.getId()).hasSize(6);

        List<UUID> ageTwo = questionRepository.findPlayableIds(topic.getId(), mode.getId(), 2, "NO_SUCH_MODE");
        assertThat(Set.copyOf(ageTwo)).isEqualTo(playable);

        // A mode that requires an object sound excludes questions without one.
        List<UUID> soundMode = questionRepository.findPlayableIds(topic.getId(), mode.getId(), 0, mode.getCode());
        assertThat(soundMode).isEmpty();

        assertThat(questionRepository.findPlayableTopicIds(6, "NO_SUCH_MODE")).contains(topic.getId());
        assertThat(questionRepository.findPlayableTopicIds(7, "NO_SUCH_MODE")).doesNotContain(topic.getId());
    }

    @Test
    void questionWithObjectSoundIsPlayableInSoundMode() {
        Topic topic = newTopic();
        GameMode mode = newMode();
        MediaAsset sound = mediaAssetRepository.save(MediaAsset.builder()
                .assetType(AssetType.ANIMAL_SOUND).name("meow-" + UUID.randomUUID())
                .storageUrl("https://example.invalid/meow.mp3").build());
        GameQuestion withSound = question(topic, mode, 4, 1, true, 1, 5);
        withSound.setObjectSound(sound);
        questionRepository.saveAndFlush(withSound);

        assertThat(questionRepository.findPlayableIds(topic.getId(), mode.getId(), 0, mode.getCode()))
                .containsExactly(withSound.getId());
    }

    @Test
    void databaseRejectsTwoCorrectOptionsForOneQuestion() {
        Topic topic = newTopic();
        GameMode mode = newMode();
        GameQuestion question = GameQuestion.builder().topic(topic).gameMode(mode).questionText("Q")
                .difficulty((short) 1).minAge((short) 1).maxAge((short) 5).build();
        question.addOption(option(1, true));
        question.addOption(option(2, true));

        assertThatThrownBy(() -> questionRepository.saveAndFlush(question))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insertIfAbsentCreatesAPlayerOnlyOnce() {
        UUID anonymousId = UUID.randomUUID();

        assertThat(playerRepository.insertIfAbsent(anonymousId, Instant.now())).isEqualTo(1);
        assertThat(playerRepository.insertIfAbsent(anonymousId, Instant.now())).isZero();
        assertThat(playerRepository.findByAnonymousId(anonymousId)).isPresent();
    }

    @Test
    void addStatsIncrementsCountersAtomically() {
        AnonymousPlayer player = newPlayer();
        playerRepository.addStats(player.getId(), 1, 5, 4, Instant.now());
        playerRepository.addStats(player.getId(), 0, 1, 1, Instant.now());
        em.clear();

        AnonymousPlayer reloaded = playerRepository.findById(player.getId()).orElseThrow();
        assertThat(reloaded.getTotalGames()).isEqualTo(1);
        assertThat(reloaded.getTotalQuestions()).isEqualTo(6);
        assertThat(reloaded.getTotalCorrect()).isEqualTo(5);
    }

    @Test
    void aStickerIsAwardedOnlyOncePerPlayer() {
        AnonymousPlayer player = newPlayer();
        GameSession session = newSession(player, newTopic(), newMode());
        Sticker sticker = stickerRepository.saveAndFlush(
                Sticker.builder().code("IT_" + UUID.randomUUID()).name("Test sticker").build());

        Instant now = Instant.now();
        assertThat(playerStickerRepository.insertIfAbsent(player.getId(), sticker.getId(), session.getId(), now))
                .isEqualTo(1);
        assertThat(playerStickerRepository.insertIfAbsent(player.getId(), sticker.getId(), session.getId(), now))
                .isZero();
        assertThat(playerStickerRepository.findBySessionId(session.getId())).hasSize(1);
        assertThat(playerStickerRepository.findByPlayerIdOrderByEarnedAtDesc(player.getId())).hasSize(1);
    }

    @Test
    void sessionCanBeLoadedWithRowLockAndInactiveOnesAreAbandoned() {
        GameSession session = newSession(newPlayer(), newTopic(), newMode());

        assertThat(sessionRepository.findBySessionIdForUpdate(session.getSessionId())).isPresent();

        Instant now = Instant.now();
        int abandoned = sessionRepository.abandonInactive(now.plus(1, ChronoUnit.HOURS), now);
        em.clear();

        assertThat(abandoned).isGreaterThanOrEqualTo(1);
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void databaseRejectsScoreThatDiffersFromCorrectAnswers() {
        GameSession session = newSession(newPlayer(), newTopic(), newMode());
        session.setCorrectAnswers((short) 2);
        session.setScore((short) 5); // score must equal correct_answers

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- fixtures

    private Topic newTopic() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return topicRepository.saveAndFlush(Topic.builder().code("IT_" + suffix).name("Topic " + suffix)
                .slug("it-" + suffix).minAge((short) 1).maxAge((short) 5).build());
    }

    private GameMode newMode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return gameModeRepository.saveAndFlush(GameMode.builder().code("IT_" + suffix).name("Mode " + suffix).build());
    }

    private AnonymousPlayer newPlayer() {
        Instant now = Instant.now();
        return playerRepository.saveAndFlush(AnonymousPlayer.builder()
                .anonymousId(UUID.randomUUID()).firstSeenAt(now).lastSeenAt(now).build());
    }

    private GameSession newSession(AnonymousPlayer player, Topic topic, GameMode mode) {
        return sessionRepository.saveAndFlush(GameSession.builder()
                .player(player).topic(topic).gameMode(mode).startedAt(Instant.now()).build());
    }

    private GameQuestion question(Topic topic, GameMode mode, int options, int correct, boolean active,
                                  int minAge, int maxAge) {
        GameQuestion question = GameQuestion.builder().topic(topic).gameMode(mode).questionText("Which one?")
                .difficulty((short) 1).minAge((short) minAge).maxAge((short) maxAge).active(active).build();
        for (int i = 1; i <= options; i++) {
            question.addOption(option(i, i <= correct));
        }
        return questionRepository.saveAndFlush(question);
    }

    private QuestionOption option(int order, boolean correct) {
        return QuestionOption.builder().optionText("Option " + order).correct(correct).displayOrder(order).build();
    }

    @SuppressWarnings("unused")
    private static Set<UUID> ids(List<GameQuestion> questions) {
        return questions.stream().map(GameQuestion::getId).collect(Collectors.toSet());
    }
}
