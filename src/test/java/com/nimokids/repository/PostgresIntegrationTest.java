package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Sticker;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.AssetType;
import com.nimokids.entity.enums.SessionStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Runs against a REAL PostgreSQL that already has the Flyway schema. Opt-in: only runs when DB_HOST is set
 * (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD are read by application-dev.yml). Every test runs in a transaction
 * that is rolled back, so no data is left behind. Fixtures use unique codes so they never clash with the dev seed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class PostgresIntegrationTest {

    private static final String SOUND_MODE = "SOUND_FOR_TEST_ONLY";

    @Autowired private EntityManager em;
    @Autowired private TopicRepository topicRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private GameQuestionRepository questionRepository;
    @Autowired private AnswerItemRepository answerItemRepository;
    @Autowired private AnonymousPlayerRepository playerRepository;
    @Autowired private GameSessionRepository sessionRepository;
    @Autowired private StickerRepository stickerRepository;
    @Autowired private PlayerStickerRepository playerStickerRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;

    // ----------------------------------------------------- explicit tag matching

    @Test
    void wrongAnswersAreFoundByPositiveTagMatchingOnly() {
        String tag = unique();
        AnswerItem bird = item("Bird", Map.of("t", tag, "category", "animal", "can_fly", true));
        AnswerItem dog = item("Dog", Map.of("t", tag, "category", "animal", "can_fly", false));
        AnswerItem cat = item("Cat", Map.of("t", tag, "category", "animal", "can_fly", false));
        AnswerItem untagged = item("Mystery", Map.of("t", tag, "category", "animal"));   // can_fly is NOT written out
        AnswerItem fish = item("Fish", Map.of("t", tag, "category", "animal", "can_fly", false, "habitat", "water"));
        AnswerItem inactive = item("Ghost", Map.of("t", tag, "category", "animal", "can_fly", false));
        inactive.setActive(false);
        answerItemRepository.saveAndFlush(inactive);

        List<AnswerItem> found = answerItemRepository.findRandomMatching(
                "{\"t\":\"" + tag + "\",\"category\":\"animal\",\"can_fly\":false}", List.of(bird.getId()), 50);

        assertThat(found).extracting(AnswerItem::getName).containsExactlyInAnyOrder("Dog", "Cat", "Fish");
        assertThat(found).extracting(AnswerItem::getName).doesNotContain("Bird", "Mystery", "Ghost");
        assertThat(dog.getId()).isNotNull();
        assertThat(cat.getId()).isNotNull();
        assertThat(fish.getId()).isNotNull();
        assertThat(untagged.getId()).isNotNull();
    }

    @Test
    void aMatchWithSeveralTagsRequiresAllOfThem() {
        String tag = unique();
        item("Cow", Map.of("t", tag, "category", "animal", "habitat", "farm"));
        item("Fish", Map.of("t", tag, "category", "animal", "habitat", "water"));
        item("Tractor", Map.of("t", tag, "category", "vehicle", "habitat", "farm"));

        List<AnswerItem> found = answerItemRepository.findRandomMatching(
                "{\"t\":\"" + tag + "\",\"category\":\"animal\",\"habitat\":\"farm\"}", List.of(UUID.randomUUID()), 10);

        assertThat(found).extracting(AnswerItem::getName).containsExactly("Cow");
    }

    @Test
    void theCorrectItemAndAlreadyChosenItemsAreExcludedByIdAndTheLimitIsHonoured() {
        String tag = unique();
        AnswerItem a = item("A", Map.of("t", tag, "group", "x"));
        AnswerItem b = item("B", Map.of("t", tag, "group", "x"));
        item("C", Map.of("t", tag, "group", "x"));
        item("D", Map.of("t", tag, "group", "x"));

        List<AnswerItem> found = answerItemRepository.findRandomMatching(
                "{\"t\":\"" + tag + "\",\"group\":\"x\"}", List.of(a.getId(), b.getId()), 10);
        assertThat(found).extracting(AnswerItem::getName).containsExactlyInAnyOrder("C", "D");

        assertThat(answerItemRepository.findRandomMatching(
                "{\"t\":\"" + tag + "\",\"group\":\"x\"}", List.of(a.getId()), 2)).hasSize(2);
    }

    @Test
    void theContainmentLookupIsServedByTheGinIndex() {
        // Tiny tables prefer a sequential scan, so forbid it for this transaction and look at the plan.
        em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
        @SuppressWarnings("unchecked")
        List<String> plan = em.createNativeQuery(
                "EXPLAIN SELECT * FROM answer_items WHERE is_active = TRUE AND metadata @> CAST('{\"category\":\"animal\"}' AS jsonb)")
                .getResultList();

        assertThat(String.join("\n", plan)).contains("idx_answer_items_metadata");
    }

    // ------------------------------------------------------------ tree + pool

    @Test
    void aTopicSubtreeContainsTheTopicAndEveryActiveDescendant() {
        Topic root = topic("root", null);
        Topic child = topic("child", root);
        Topic grandChild = topic("grandchild", child);
        Topic inactiveChild = topic("inactive", root);
        inactiveChild.setActive(false);
        topicRepository.saveAndFlush(inactiveChild);
        Topic belowInactive = topic("below-inactive", inactiveChild);
        Topic unrelated = topic("unrelated", null);

        List<UUID> subtree = topicRepository.findActiveSubtreeIds(root.getId());

        assertThat(subtree).containsExactlyInAnyOrder(root.getId(), child.getId(), grandChild.getId());
        assertThat(subtree).doesNotContain(inactiveChild.getId(), belowInactive.getId(), unrelated.getId());
        assertThat(topicRepository.findActiveSubtreeIds(child.getId())).containsExactlyInAnyOrder(child.getId(), grandChild.getId());
    }

    @Test
    void theSubtreeLookupTerminatesEvenIfBadDataContainsACycle() {
        Topic a = topic("cycle-a", null);
        Topic b = topic("cycle-b", a);
        a.setParentTopic(b);
        topicRepository.saveAndFlush(a);

        assertThat(topicRepository.findActiveSubtreeIds(a.getId())).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void aTopicCannotBeItsOwnParent() {
        Topic t = topic("self", null);
        t.setParentTopic(t);

        assertThatThrownBy(() -> topicRepository.saveAndFlush(t)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void candidateQuestionsApplyEveryRuleAndOnlyLookInsideTheGivenTopics() {
        GameMode mode = mode("GUESS_" + unique());
        Topic parent = topic("parent", null);
        Topic child = topic("child", parent);
        Topic other = topic("other", null);
        AnswerItem correct = item("Correct", Map.of("category", "animal"));
        AnswerItem inactiveItem = item("Inactive", Map.of("category", "animal"));
        inactiveItem.setActive(false);
        answerItemRepository.saveAndFlush(inactiveItem);

        GameQuestion ownQuestion = question(parent, mode, correct, rules());
        GameQuestion childQuestion = question(child, mode, correct, rules());
        GameQuestion otherTopic = question(other, mode, correct, rules());
        GameQuestion inactiveQuestion = question(child, mode, correct, rules());
        inactiveQuestion.setActive(false);
        questionRepository.saveAndFlush(inactiveQuestion);
        question(child, mode, inactiveItem, rules());                                        // inactive correct item
        question(child, mode, correct, new HashMap<>());                                     // no distractor rules
        GameQuestion tooYoung = question(child, mode, correct, rules());
        tooYoung.setMinAge((short) 4);
        tooYoung.setMaxAge((short) 5);
        questionRepository.saveAndFlush(tooYoung);

        List<UUID> subtree = topicRepository.findActiveSubtreeIds(parent.getId());

        assertThat(questionRepository.findCandidateIds(subtree, mode.getId(), 0, SOUND_MODE))
                .containsExactlyInAnyOrder(ownQuestion.getId(), childQuestion.getId(), tooYoung.getId());
        assertThat(questionRepository.findCandidateIds(subtree, mode.getId(), 2, SOUND_MODE))
                .containsExactlyInAnyOrder(ownQuestion.getId(), childQuestion.getId())
                .doesNotContain(tooYoung.getId(), otherTopic.getId());
        assertThat(questionRepository.findCandidateIds(List.of(other.getId()), mode.getId(), 0, SOUND_MODE))
                .containsExactly(otherTopic.getId());
    }

    @Test
    void aSoundModeOnlyOffersQuestionsThatHaveAnObjectSound() {
        GameMode mode = mode(SOUND_MODE);
        Topic topic = topic("sounds", null);
        AnswerItem correct = item("Cat", Map.of("category", "animal"));
        GameQuestion silent = question(topic, mode, correct, rules());
        GameQuestion withSound = question(topic, mode, correct, rules());
        withSound.setObjectSound(mediaAssetRepository.save(MediaAsset.builder()
                .assetType(AssetType.ANIMAL_SOUND).name("meow-" + unique()).storageUrl("https://example.invalid/meow.mp3").build()));
        questionRepository.saveAndFlush(withSound);

        assertThat(questionRepository.findCandidateIds(List.of(topic.getId()), mode.getId(), 0, SOUND_MODE))
                .containsExactly(withSound.getId())
                .doesNotContain(silent.getId());
    }

    @Test
    void aParentTopicIsPlayableThroughItsChildrenAndTheThresholdCountsTheWholeSubtree() {
        GameMode mode = mode("GUESS_" + unique());
        Topic parent = topic("parent", null);
        Topic childA = topic("a", parent);
        Topic childB = topic("b", parent);
        AnswerItem correct = item("Correct", Map.of("category", "animal"));
        for (int i = 0; i < 3; i++) {
            question(childA, mode, correct, rules());
        }
        for (int i = 0; i < 2; i++) {
            question(childB, mode, correct, rules());
        }

        List<UUID> playableFive = questionRepository.findPlayableTopicIds(5, SOUND_MODE);
        List<UUID> playableSix = questionRepository.findPlayableTopicIds(6, SOUND_MODE);

        assertThat(playableFive).contains(parent.getId()).doesNotContain(childA.getId(), childB.getId());
        assertThat(playableSix).doesNotContain(parent.getId());
    }

    // ------------------------------------------------------ snapshot persistence

    @Test
    void theOptionsSnapshotSurvivesAJsonbRoundTripExactly() {
        GameMode mode = mode("GUESS_" + unique());
        Topic topic = topic("snapshot", null);
        AnswerItem correct = item("Bird", Map.of("category", "animal"));
        GameQuestion question = question(topic, mode, correct, rules());
        GameSession session = newSession(newPlayer(), topic, mode);

        List<SnapshotOption> snapshot = List.of(
                new SnapshotOption(UUID.randomUUID(), correct.getId(), "Bird", "https://x/b.png", "https://x/b.mp3", 1, true),
                new SnapshotOption(UUID.randomUUID(), UUID.randomUUID(), "Dog", null, null, 2, false),
                new SnapshotOption(UUID.randomUUID(), UUID.randomUUID(), "Cat", null, null, 3, false),
                new SnapshotOption(UUID.randomUUID(), UUID.randomUUID(), "Cow", null, null, 4, false));
        Instant presented = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        session.addSessionQuestion(SessionQuestion.builder().question(question).questionNumber((short) 1)
                .optionsSnapshot(snapshot).presentedAt(presented).selectedOptionId(snapshot.get(0).optionId()).build());
        sessionRepository.saveAndFlush(session);
        em.clear();

        SessionQuestion reloaded = sessionRepository.findBySessionId(session.getSessionId()).orElseThrow()
                .getSessionQuestions().get(0);

        assertThat(reloaded.getOptionsSnapshot()).isEqualTo(snapshot);
        assertThat(reloaded.getOptionsSnapshot().get(0).isCorrect()).isTrue();
        assertThat(reloaded.getSelectedOptionId()).isEqualTo(snapshot.get(0).optionId());
        assertThat(reloaded.getPresentedAt()).isEqualTo(presented);
        assertThat(reloaded.getTimerStartedAt()).isNull();
    }

    @Test
    void theDatabaseGuardsTheShapeOfTheJsonColumns() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("insert into answer_items (code, name, metadata) values ('BAD_" + unique() + "', 'x', CAST('[]' AS jsonb))").executeUpdate();
        }).hasStackTraceContaining("ck_answer_items_metadata_object");
    }

    // ------------------------------------------------------ players and sessions

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
        GameSession session = newSession(player, topic("sticker", null), mode("GUESS_" + unique()));
        Sticker sticker = stickerRepository.saveAndFlush(
                Sticker.builder().code("IT_" + UUID.randomUUID()).name("Test sticker").build());

        Instant now = Instant.now();
        assertThat(playerStickerRepository.insertIfAbsent(player.getId(), sticker.getId(), session.getId(), now)).isEqualTo(1);
        assertThat(playerStickerRepository.insertIfAbsent(player.getId(), sticker.getId(), session.getId(), now)).isZero();
        assertThat(playerStickerRepository.findBySessionId(session.getId())).hasSize(1);
        assertThat(playerStickerRepository.findByPlayerIdOrderByEarnedAtDesc(player.getId())).hasSize(1);
    }

    @Test
    void sessionCanBeLoadedWithRowLockAndInactiveOnesAreAbandoned() {
        GameSession session = newSession(newPlayer(), topic("lock", null), mode("GUESS_" + unique()));

        assertThat(sessionRepository.findBySessionIdForUpdate(session.getSessionId())).isPresent();

        Instant now = Instant.now();
        int abandoned = sessionRepository.abandonInactive(now.plus(1, ChronoUnit.HOURS), now);
        em.clear();

        assertThat(abandoned).isGreaterThanOrEqualTo(1);
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus()).isEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void databaseRejectsScoreThatDiffersFromCorrectAnswers() {
        GameSession session = newSession(newPlayer(), topic("score", null), mode("GUESS_" + unique()));
        session.setCorrectAnswers((short) 2);
        session.setScore((short) 5);

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- fixtures

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> rules() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("distractor_rules", List.of(Map.of("match", Map.of("category", "animal"), "count", 3)));
        return metadata;
    }

    private AnswerItem item(String name, Map<String, Object> tags) {
        return answerItemRepository.saveAndFlush(AnswerItem.builder()
                .code("IT_" + unique() + "_" + name.toUpperCase()).name(name).metadata(new HashMap<>(tags)).build());
    }

    private Topic topic(String label, Topic parent) {
        String suffix = unique();
        return topicRepository.saveAndFlush(Topic.builder().code("IT_" + suffix).name(label + " " + suffix)
                .slug("it-" + suffix).minAge((short) 1).maxAge((short) 5).parentTopic(parent).build());
    }

    private GameMode mode(String code) {
        return gameModeRepository.findByCode(code)
                .orElseGet(() -> gameModeRepository.saveAndFlush(GameMode.builder().code(code).name("Mode " + code).build()));
    }

    private GameQuestion question(Topic topic, GameMode mode, AnswerItem correct, Map<String, Object> metadata) {
        return questionRepository.saveAndFlush(GameQuestion.builder().topic(topic).gameMode(mode)
                .questionText("Which one?").correctAnswerItem(correct).difficulty((short) 1)
                .minAge((short) 1).maxAge((short) 5).metadata(metadata).build());
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
}
