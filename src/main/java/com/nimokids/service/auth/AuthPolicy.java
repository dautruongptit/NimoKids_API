package com.nimokids.service.auth;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The session and token policy. One set of values for admins and users (owner decision D-03); only the concurrent
 * session cap differs. Values come from the single row of auth_settings; a missing or invalid key falls back to the
 * default, so a bad setting can never lock everybody out.
 *
 * @param maxSessionsAdmin / maxSessionsUser  null = unlimited; when the cap is reached the OLDEST session is ended
 */
public record AuthPolicy(
        Duration accessTtl,
        Duration refreshTtl,
        Duration idleTimeout,
        Duration absoluteLifetime,
        boolean refreshRotation,
        Duration reuseGrace,
        Duration lastSeenThrottle,
        Integer maxSessionsAdmin,
        Integer maxSessionsUser,
        boolean detectNewDevice) {

    public static AuthPolicy defaults() {
        return new AuthPolicy(
                Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofHours(24), Duration.ofDays(7),
                true, Duration.ofSeconds(10), Duration.ofSeconds(60), 5, null, true);
    }

    /** Reads the settings JSON ({@code accessTtlMinutes}, {@code refreshTtlDays}, ...); unknown keys are ignored. */
    public static AuthPolicy from(Map<String, Object> config) {
        AuthPolicy d = defaults();
        if (config == null) {
            return d;
        }
        long access = number(config, "accessTtlMinutes", d.accessTtl.toMinutes(), 5, 60);
        long refreshDays = number(config, "refreshTtlDays", d.refreshTtl.toDays(), 1, 30);
        long idle = number(config, "idleMinutes", d.idleTimeout.toMinutes(), 5, 60L * 24 * 30);
        long absoluteDays = number(config, "absoluteDays", d.absoluteLifetime.toDays(), 1, 90);
        long grace = number(config, "reuseGraceSeconds", d.reuseGrace.toSeconds(), 0, 60);
        long throttle = number(config, "lastSeenThrottleSeconds", d.lastSeenThrottle.toSeconds(), 10, 600);
        Duration absolute = Duration.ofDays(absoluteDays);
        Duration idleTimeout = Duration.ofMinutes(idle);
        if (idleTimeout.compareTo(absolute) > 0) {
            idleTimeout = absolute;   // idle can never be longer than the absolute limit
        }
        return new AuthPolicy(
                Duration.ofMinutes(access), Duration.ofDays(refreshDays), idleTimeout, absolute,
                bool(config, "refreshRotation", true), Duration.ofSeconds(grace), Duration.ofSeconds(throttle),
                cap(config, "maxSessionsAdmin", d.maxSessionsAdmin), cap(config, "maxSessionsUser", d.maxSessionsUser),
                bool(config, "detectNewDevice", true));
    }

    /** What is stored in user_sessions.policy_snapshot. */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accessTtlMinutes", accessTtl.toMinutes());
        map.put("refreshTtlDays", refreshTtl.toDays());
        map.put("idleMinutes", idleTimeout.toMinutes());
        map.put("absoluteDays", absoluteLifetime.toDays());
        map.put("refreshRotation", refreshRotation);
        map.put("reuseGraceSeconds", reuseGrace.toSeconds());
        return map;
    }

    private static long number(Map<String, Object> config, String key, long fallback, long min, long max) {
        Object value = config.get(key);
        if (value instanceof Number n && n.longValue() >= min && n.longValue() <= max) {
            return n.longValue();
        }
        return fallback;
    }

    private static boolean bool(Map<String, Object> config, String key, boolean fallback) {
        return config.get(key) instanceof Boolean b ? b : fallback;
    }

    private static Integer cap(Map<String, Object> config, String key, Integer fallback) {
        if (!config.containsKey(key)) {
            return fallback;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof Number n && n.intValue() >= 1 && n.intValue() <= 100 ? Integer.valueOf(n.intValue()) : fallback;
    }
}
