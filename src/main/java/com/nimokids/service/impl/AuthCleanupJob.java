package com.nimokids.service.impl;

import com.nimokids.service.auth.AuthPolicyService;
import com.nimokids.service.auth.AuthSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping of the session system: marks overdue sessions (every minute; correctness never depends on it because
 * every check also compares timestamps) and applies the retention of the history tables (hourly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCleanupJob {

    private static final int LOGIN_HISTORY_DAYS = 180;
    private static final int SECURITY_EVENT_DAYS = 365;
    private static final int AUDIT_EVENT_DAYS = 730;
    private static final int IP_GENERALISE_AFTER_DAYS = 30;

    private final AuthSessionService sessionService;
    private final com.nimokids.repository.OAuthLoginAttemptRepository attempts;
    private final java.time.Clock clock;
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void expireSessions() {
        try {
            sessionService.cleanup();
            purgeAttempts();
        } catch (RuntimeException ex) {
            log.warn("Auth cleanup failed: {}", ex.getClass().getSimpleName());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    void purgeAttempts() {
        attempts.deleteExpiredBefore(clock.instant().minus(java.time.Duration.ofDays(1)));
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    public void applyRetention() {
        try {
            jdbc.query("select * from auth_apply_retention(?, ?, ?, ?)", rs -> {
                long deleted = rs.getLong(1) + rs.getLong(2);
                long generalised = rs.getLong(3);
                if (deleted + generalised > 0) {
                    log.info("Auth retention: {} history rows deleted, {} IP addresses generalised", deleted, generalised);
                }
            }, LOGIN_HISTORY_DAYS, SECURITY_EVENT_DAYS, AUDIT_EVENT_DAYS, IP_GENERALISE_AFTER_DAYS);
        } catch (RuntimeException ex) {
            log.warn("Auth retention failed: {}", ex.getClass().getSimpleName());
        }
    }
}
