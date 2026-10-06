package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.ApiLog;
import com.nimokids.entity.AdminUser;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.Sticker;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.StickerRarity;
import com.nimokids.entity.enums.AdminRole;
import com.nimokids.logging.ApiLogEvent;
import com.nimokids.service.impl.ApiLogServiceImpl;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real PostgreSQL, opt-in (DB_HOST). Flyway runs on startup with the dev locations, so this also proves that
 * V2 and the dev seed apply cleanly. Tests are rolled back unless stated otherwise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApiLogServiceImpl.class)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class SeedAndApiLogIntegrationTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired private EntityManager em;
    @Autowired private AdminUserRepository userRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private GameQuestionRepository questionRepository;
    @Autowired private StickerRepository stickerRepository;
    @Autowired private AnonymousPlayerRepository playerRepository;
    @Autowired private ApiLogRepository apiLogRepository;
    @Autowired private ApiLogServiceImpl apiLogService;

    // ------------------------------------------------------------------- seed

    @Test
    void seedCreatesASuperAdminAndAnAdminWithBcryptPasswords() {
        AdminUser superAdmin = userRepository.findByEmailIgnoreCase("ADMIN@nimokids.local").orElseThrow();
        AdminUser staff = userRepository.findByEmailIgnoreCase("staff@nimokids.local").orElseThrow();

        assertThat(superAdmin.getEmail()).isEqualTo("admin@nimokids.local");
        assertThat(superAdmin.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(superAdmin.isActive()).isTrue();
        assertThat(superAdmin.getPasswordHash()).startsWith("$2a$10$").doesNotContain("admin123");
        assertThat(encoder.matches("admin123", superAdmin.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong", superAdmin.getPasswordHash())).isFalse();
        assertThat(staff.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(encoder.matches("admin123", staff.getPasswordHash())).isTrue();
    }

    @Test
    void adminUsersOnlyAcceptTheKnownRoles() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                em.createNativeQuery("insert into admin_users (email, password_hash, role) values ('player@nimokids.local', 'x', 'USER')").executeUpdate())
                .hasStackTraceContaining("ck_admin_users_role");
    }

    @Test
    void emailMustLookLikeAnEmail() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                em.createNativeQuery("insert into admin_users (email, password_hash, role) values ('no-at-sign', 'x', 'ADMIN')").executeUpdate())
                .hasStackTraceContaining("ck_admin_users_email_format");
    }

    @Test
    void duplicateEmailInADifferentCaseIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                em.createNativeQuery("insert into admin_users (email, password_hash, role) values ('ADMIN@NimoKids.Local', 'x', 'ADMIN')").executeUpdate())
                .hasStackTraceContaining("uk_admin_users_email_lower");
    }

    @Test
    void seededTopicsAreBothPlayableWithFivePlayableQuestions() {
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();
        Topic animals = topicRepository.findByCode("ANIMALS").orElseThrow();
        Topic math = topicRepository.findByCode("MATH").orElseThrow();

        assertThat(questionRepository.findPlayableIds(animals.getId(), mode.getId(), 0)).hasSize(5);
        assertThat(questionRepository.findPlayableIds(math.getId(), mode.getId(), 0)).hasSize(5);
        assertThat(questionRepository.findPlayableTopicIds(5)).contains(animals.getId(), math.getId());
        assertThat(topicRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .extracting(Topic::getCode).containsSubsequence("ANIMALS", "MATH");
    }

    @Test
    void everySeededQuestionHasFourOptionsAndExactlyOneCorrect() {
        List<GameQuestion> questions = em.createQuery(
                        "select q from GameQuestion q where q.topic.code in ('ANIMALS', 'MATH')", GameQuestion.class)
                .getResultList();

        assertThat(questions).hasSize(10);
        for (GameQuestion question : questions) {
            assertThat(question.getOptions()).hasSize(4);
            assertThat(question.getOptions().stream().filter(o -> o.isCorrect()).count()).isEqualTo(1);
        }
    }

    @Test
    void seedCreatesThreeStickers() {
        assertThat(stickerRepository.findByCode("FIRST_GAME")).get().extracting(Sticker::getRarity).isEqualTo(StickerRarity.COMMON);
        assertThat(stickerRepository.findByCode("PERFECT_SCORE")).get().extracting(Sticker::getRarity).isEqualTo(StickerRarity.RARE);
        assertThat(stickerRepository.findByCode("ANIMAL_LOVER")).isPresent();
    }

    // ---------------------------------------------------------------- api log

    @Test
    void apiLogRowIsStoredWithPlayerIpAndErrorDetails() {
        AnonymousPlayer player = playerRepository.saveAndFlush(AnonymousPlayer.builder()
                .anonymousId(UUID.randomUUID()).firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).build());
        UUID requestId = UUID.randomUUID();

        apiLogService.record(new ApiLogEvent(requestId, "POST", "/api/v1/game-sessions/{sessionId}/submit-answer",
                409, 12, "203.0.113.7", "JUnit", 80, 140, "QUESTION_ALREADY_ANSWERED", "Question has already been answered",
                player.getAnonymousId(), null));
        em.flush();
        em.clear();

        ApiLog stored = apiLogRepository.findByRequestId(requestId).orElseThrow();
        assertThat(stored.getHttpMethod()).isEqualTo("POST");
        assertThat(stored.getEndpoint()).isEqualTo("/api/v1/game-sessions/{sessionId}/submit-answer");
        assertThat(stored.getStatusCode()).isEqualTo((short) 409);
        assertThat(stored.getResponseTimeMs()).isEqualTo(12);
        assertThat(stored.getIpAddress().getHostAddress()).isEqualTo("203.0.113.7");
        assertThat(stored.getErrorCode()).isEqualTo("QUESTION_ALREADY_ANSWERED");
        assertThat(stored.getPlayer().getId()).isEqualTo(player.getId());
        assertThat(stored.getRequestSizeBytes()).isEqualTo(80);
    }

    @Test
    void unparsableIpAndUnknownPlayerAreStoredAsNull() {
        UUID requestId = UUID.randomUUID();

        apiLogService.record(new ApiLogEvent(requestId, "GET", "/api/v1/topics", 200, 3,
                "not-an-ip; drop table", null, null, 20, null, null, UUID.randomUUID(), UUID.randomUUID()));
        em.flush();
        em.clear();

        ApiLog stored = apiLogRepository.findByRequestId(requestId).orElseThrow();
        assertThat(stored.getIpAddress()).isNull();
        assertThat(stored.getPlayer()).isNull();
        assertThat(stored.getSession()).isNull();
    }

    /** Committed on purpose (no wrapping transaction) to mimic production, where each save is its own transaction. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reusingARequestIdKeepsOneRowAndDoesNotThrow() {
        UUID requestId = UUID.randomUUID();
        ApiLogEvent event = new ApiLogEvent(requestId, "GET", "/api/v1/topics", 200, 3,
                "127.0.0.1", null, null, 20, null, null, null, null);
        try {
            apiLogService.record(event);
            apiLogService.record(event);

            assertThat(apiLogRepository.findByRequestId(requestId)).isPresent();
        } finally {
            apiLogRepository.findByRequestId(requestId).ifPresent(apiLogRepository::delete);
        }
        assertThat(apiLogRepository.findByRequestId(requestId)).isEmpty();
    }
}
