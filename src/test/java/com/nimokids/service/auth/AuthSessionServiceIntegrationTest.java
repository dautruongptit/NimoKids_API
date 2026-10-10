package com.nimokids.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimokids.entity.AdminUser;
import com.nimokids.entity.AuthSession;
import com.nimokids.entity.enums.AdminRole;
import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.AuthSessionStatus;
import com.nimokids.entity.enums.PrincipalType;
import com.nimokids.entity.enums.SessionEndReason;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AdminUserRepository;
import com.nimokids.repository.AuthSessionRepository;
import com.nimokids.security.JwtProperties;
import com.nimokids.security.JwtService;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The session rules against a real PostgreSQL (the partial unique index "one ACTIVE refresh token per session", the
 * row lock and the immutability trigger can only be proven there). Time is a controllable clock. Everything is rolled
 * back, except security events, which are written in their own transaction by design (they carry no foreign key).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuthSessionService.class, AuthPolicyService.class, SecurityEventService.class, ClientIpResolver.class,
        JwtService.class, AuthSessionServiceIntegrationTest.TestBeans.class})
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class AuthSessionServiceIntegrationTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock();
        }

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("bmltb2tpZHMtZGV2LW9ubHktand0LXNlY3JldC1rZXktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTAxMjM0NTY3ODk=", 60);
        }
    }

    @Autowired private AuthSessionService service;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private AdminUserRepository admins;
    @Autowired private MutableClock clock;
    @Autowired private EntityManager em;
    @Autowired private AuthPolicyService policyService;

    private AuthSessionService.Principal admin;
    private ClientContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        AdminUser created = admins.saveAndFlush(AdminUser.builder().email("it-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x".repeat(60)).role(AdminRole.SUPER_ADMIN).build());
        admin = new AuthSessionService.Principal(PrincipalType.ADMIN, created.getId(), "SUPER_ADMIN");
        ctx = new ClientContext(InetAddress.getByName("113.22.10.4"), "UA", "Chrome", "Windows", "DESKTOP", new byte[]{1, 2, 3}, null);
    }

    private AuthSessionService.IssuedTokens login() {
        return service.createSession(admin, AuthMethod.PASSWORD, ctx);
    }

    private AuthSessionService.RefreshOutcome refresh(String raw) {
        em.flush();
        em.clear();
        return service.refresh(raw, ctx);
    }

    @Test
    void aNewSessionHasFixedAbsoluteAndIdleLimitsAndAHashedRefreshToken() {
        AuthSessionService.IssuedTokens issued = login();
        AuthSession session = issued.session();

        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        assertThat(session.getIdleExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        assertThat(session.getLoginIp().getHostAddress()).isEqualTo("113.22.10.4");
        assertThat(issued.refreshToken()).hasSize(43);
        em.flush();
        Number stored = (Number) em.createNativeQuery("select count(*) from refresh_tokens where session_id = :id and token_hash = :h")
                .setParameter("id", session.getId()).setParameter("h", TokenHasher.sha256(issued.refreshToken())).getSingleResult();
        assertThat(stored.intValue()).isEqualTo(1);
        Number raw = (Number) em.createNativeQuery("select count(*) from refresh_tokens where encode(token_hash, 'escape') = :raw")
                .setParameter("raw", issued.refreshToken()).getSingleResult();
        assertThat(raw.intValue()).as("the raw token is never stored").isZero();
    }

    @Test
    void refreshRotatesTheTokenAndMovesTheIdleDeadlineButNeverTheAbsoluteOne() {
        AuthSessionService.IssuedTokens first = login();
        Instant absolute = first.session().getAbsoluteExpiresAt();
        clock.advance(Duration.ofHours(5));

        AuthSessionService.RefreshOutcome outcome = refresh(first.refreshToken());

        assertThat(outcome.ok()).isTrue();
        assertThat(outcome.tokens().refreshToken()).isNotNull().isNotEqualTo(first.refreshToken());
        AuthSession session = sessions.findById(first.session().getId()).orElseThrow();
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(absolute);
        assertThat(session.getIdleExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
    }

    @Test
    void theIdleDeadlineIsCappedByTheAbsoluteLimit() {
        AuthSessionService.IssuedTokens issued = login();
        String token = issued.refreshToken();
        for (int day = 0; day < 6; day++) {
            clock.advance(Duration.ofHours(23));
            token = refresh(token).tokens().refreshToken();
        }
        clock.advance(Duration.ofHours(22));   // 6 x 23 h + 22 h = 160 h, 8 h before the 168 h absolute limit

        AuthSessionService.RefreshOutcome outcome = refresh(token);

        assertThat(outcome.ok()).isTrue();
        AuthSession session = sessions.findById(issued.session().getId()).orElseThrow();
        assertThat(session.getIdleExpiresAt()).isEqualTo(session.getAbsoluteExpiresAt());
        assertThat(outcome.tokens().accessExpiresAt()).isBeforeOrEqualTo(session.getAbsoluteExpiresAt());
    }

    @Test
    void refreshAfterTheAbsoluteLimitFailsEvenIfTheTokenIsUsedEveryDay() {
        AuthSessionService.IssuedTokens issued = login();
        String token = issued.refreshToken();
        for (int hour = 0; hour < 7 * 24; hour += 20) {
            clock.advance(Duration.ofHours(20));
            AuthSessionService.RefreshOutcome outcome = refresh(token);
            if (!outcome.ok()) {
                assertThat(outcome.error()).isEqualTo(ErrorCode.SESSION_EXPIRED);
                return;
            }
            token = outcome.tokens().refreshToken();
        }
        clock.advance(Duration.ofHours(30));
        assertThat(refresh(token).error()).isEqualTo(ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void idleLongerThanThePolicyEndsTheSession() {
        AuthSessionService.IssuedTokens issued = login();
        clock.advance(Duration.ofHours(25));

        AuthSessionService.RefreshOutcome outcome = refresh(issued.refreshToken());

        assertThat(outcome.ok()).isFalse();
        assertThat(outcome.error()).isEqualTo(ErrorCode.SESSION_EXPIRED);
        AuthSession session = sessions.findById(issued.session().getId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.EXPIRED);
        assertThat(session.getEndedReason()).isEqualTo(SessionEndReason.IDLE);
    }

    @Test
    void theOldTokenInsideTheGraceWindowIsABenignRaceAndGetsNoNewRefreshToken() {
        AuthSessionService.IssuedTokens first = login();
        refresh(first.refreshToken());
        clock.advance(Duration.ofSeconds(5));

        AuthSessionService.RefreshOutcome race = refresh(first.refreshToken());

        assertThat(race.ok()).isTrue();
        assertThat(race.tokens().refreshToken()).as("the browser already holds the successor").isNull();
        assertThat(race.tokens().accessToken()).isNotBlank();
        assertThat(sessions.findById(first.session().getId()).orElseThrow().getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
    }

    @Test
    void theOldTokenAfterTheGraceWindowIsReuseAndEndsOnlyThatSession() {
        AuthSessionService.IssuedTokens first = login();
        AuthSessionService.IssuedTokens otherDevice = login();
        AuthSessionService.RefreshOutcome rotated = refresh(first.refreshToken());
        clock.advance(Duration.ofSeconds(11));

        AuthSessionService.RefreshOutcome reuse = refresh(first.refreshToken());

        assertThat(reuse.error()).isEqualTo(ErrorCode.REFRESH_REUSED);
        assertThat(sessions.findById(first.session().getId()).orElseThrow().getEndedReason()).isEqualTo(SessionEndReason.REFRESH_REUSE);
        assertThat(refresh(rotated.tokens().refreshToken()).error()).as("the thief and the owner are both out").isEqualTo(ErrorCode.SESSION_REVOKED);
        assertThat(refresh(otherDevice.refreshToken()).ok()).as("another device keeps its session").isTrue();
    }

    @Test
    void unknownAndMalformedTokensAreRefreshInvalid() {
        assertThat(refresh(TokenHasher.newRawToken()).error()).isEqualTo(ErrorCode.REFRESH_INVALID);
        assertThat(refresh("short").error()).isEqualTo(ErrorCode.REFRESH_INVALID);
        assertThat(refresh(null).error()).isEqualTo(ErrorCode.REFRESH_INVALID);
    }

    @Test
    void aDisabledAccountCannotRefreshAndItsSessionEnds() {
        AuthSessionService.IssuedTokens issued = login();
        AdminUser account = admins.findById(admin.id()).orElseThrow();
        account.setActive(false);
        admins.saveAndFlush(account);

        AuthSessionService.RefreshOutcome outcome = refresh(issued.refreshToken());

        assertThat(outcome.error()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        assertThat(sessions.findById(issued.session().getId()).orElseThrow().getEndedReason()).isEqualTo(SessionEndReason.ACCOUNT_DISABLED);
    }

    @Test
    void logoutEndsTheSessionAndItsTokensAndIsIdempotent() {
        AuthSessionService.IssuedTokens issued = login();

        assertThat(service.logout(issued.session().getId(), SessionEndReason.LOGOUT)).isTrue();
        assertThat(service.logout(issued.session().getId(), SessionEndReason.LOGOUT)).isTrue();
        assertThat(service.logout(UUID.randomUUID(), SessionEndReason.LOGOUT)).isFalse();

        assertThat(refresh(issued.refreshToken()).error()).isEqualTo(ErrorCode.SESSION_REVOKED);
        assertThat(service.check(issued.session().getId(), new org.springframework.mock.web.MockHttpServletRequest()))
                .isEqualTo(SessionGuard.Result.REVOKED);
    }

    @Test
    void theGuardAcceptsALiveSessionAndRefusesAnUnknownOne() {
        AuthSessionService.IssuedTokens issued = login();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();

        assertThat(service.check(issued.session().getId(), request)).isEqualTo(SessionGuard.Result.OK);
        assertThat(service.check(UUID.randomUUID(), request)).isEqualTo(SessionGuard.Result.REVOKED);
        clock.advance(Duration.ofDays(8));
        assertThat(service.check(issued.session().getId(), request)).isEqualTo(SessionGuard.Result.EXPIRED);
    }

    @Test
    void anAdminCannotHoldMoreSessionsThanTheCapTheOldestIsEnded() {
        AuthSessionService.IssuedTokens oldest = login();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofMinutes(1));
            login();
        }
        assertThat(service.listOwn(admin, null)).hasSize(5);

        clock.advance(Duration.ofMinutes(1));
        login();   // the 6th

        assertThat(service.listOwn(admin, null)).hasSize(5);
        assertThat(sessions.findById(oldest.session().getId()).orElseThrow().getEndedReason()).isEqualTo(SessionEndReason.EVICTED);
    }

    @Test
    void sessionsOfOtherAccountsAreInvisibleAndCannotBeRevoked() {
        AuthSessionService.IssuedTokens mine = login();
        AdminUser stranger = admins.saveAndFlush(AdminUser.builder().email("other-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x".repeat(60)).role(AdminRole.ADMIN).build());
        AuthSessionService.Principal other = new AuthSessionService.Principal(PrincipalType.ADMIN, stranger.getId(), "ADMIN");

        assertThat(service.listOwn(other, null)).isEmpty();
        assertThat(service.revokeOwn(other, mine.session().getId(), ctx)).isFalse();
        assertThat(sessions.findById(mine.session().getId()).orElseThrow().getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(service.revokeOwn(admin, mine.session().getId(), ctx)).isTrue();
    }

    @Test
    void logoutAllKeepsTheCurrentSessionWhenAsked() {
        AuthSessionService.IssuedTokens keep = login();
        login();
        login();

        int revoked = service.logoutAll(admin, keep.session().getId(), ctx);

        assertThat(revoked).isEqualTo(2);
        assertThat(service.listOwn(admin, keep.session().getId())).extracting(AuthSessionService.SessionView::id)
                .containsExactly(keep.session().getId());
    }

    @Test
    void theHistoryTablesRejectUpdateAndDelete() {
        em.createNativeQuery("insert into login_history (occurred_at, method, outcome, ip) values (now(), 'PASSWORD', 'FAILED', '113.22.10.4')")
                .executeUpdate();

        assertThatThrownBy(() -> {
            em.createNativeQuery("update login_history set outcome = 'SUCCESS' where outcome = 'FAILED'").executeUpdate();
            em.flush();
        }).hasMessageContaining("append-only");
    }

    @Test
    void theRetentionFunctionPurgesOldHistoryAndGeneralisesOldIps() {
        em.createNativeQuery("insert into login_history (occurred_at, method, outcome, ip, failure_code) values "
                + "(now() - interval '400 days', 'PASSWORD', 'FAILED', '113.22.10.4', 'RET_OLD'), "
                + "(now() - interval '45 days',  'PASSWORD', 'FAILED', '113.22.10.4', 'RET_MID'), "
                + "(now() - interval '2 days',   'PASSWORD', 'FAILED', '113.22.10.4', 'RET_NEW')").executeUpdate();

        em.createNativeQuery("select * from auth_apply_retention(180, 365, 730, 30)").getResultList();

        assertThat(count("RET_OLD")).as("older than the retention is deleted").isZero();
        assertThat(ipOf("RET_MID")).as("older than 30 days: generalised, host bits zeroed").isEqualTo("113.22.10.0/24");
        assertThat(ipOf("RET_NEW")).as("recent rows keep the full IP").isEqualTo("113.22.10.4/32");
    }

    private int count(String code) {
        return ((Number) em.createNativeQuery("select count(*) from login_history where failure_code = :c")
                .setParameter("c", code).getSingleResult()).intValue();
    }

    private String ipOf(String code) {
        return (String) em.createNativeQuery("select text(ip) from login_history where failure_code = :c")
                .setParameter("c", code).getSingleResult();
    }
}
