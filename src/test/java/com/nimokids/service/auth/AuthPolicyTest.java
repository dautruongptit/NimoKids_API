package com.nimokids.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthPolicyTest {

    @Test
    void defaultsAreTheProposedUnifiedPolicy() {
        AuthPolicy policy = AuthPolicy.defaults();

        assertThat(policy.accessTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.refreshTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(policy.idleTimeout()).isEqualTo(Duration.ofHours(24));
        assertThat(policy.absoluteLifetime()).isEqualTo(Duration.ofDays(7));
        assertThat(policy.refreshRotation()).isTrue();
        assertThat(policy.reuseGrace()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.maxSessionsAdmin()).isEqualTo(5);
        assertThat(policy.maxSessionsUser()).isNull();
    }

    @Test
    void settingsOverrideTheDefaults() {
        AuthPolicy policy = AuthPolicy.from(Map.of("accessTtlMinutes", 30, "refreshTtlDays", 3, "idleMinutes", 60,
                "absoluteDays", 14, "reuseGraceSeconds", 20, "maxSessionsUser", 3));

        assertThat(policy.accessTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.refreshTtl()).isEqualTo(Duration.ofDays(3));
        assertThat(policy.idleTimeout()).isEqualTo(Duration.ofMinutes(60));
        assertThat(policy.absoluteLifetime()).isEqualTo(Duration.ofDays(14));
        assertThat(policy.reuseGrace()).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.maxSessionsUser()).isEqualTo(3);
    }

    @Test
    void aValueOutsideItsRangeOrOfTheWrongTypeFallsBackToTheDefault() {
        Map<String, Object> bad = new HashMap<>();
        bad.put("accessTtlMinutes", 100000);   // far too long
        bad.put("refreshTtlDays", -2);
        bad.put("idleMinutes", "soon");
        bad.put("refreshRotation", "yes");
        bad.put("maxSessionsAdmin", 0);

        AuthPolicy policy = AuthPolicy.from(bad);

        assertThat(policy).isEqualTo(AuthPolicy.defaults());
    }

    @Test
    void idleTimeoutCanNeverBeLongerThanTheAbsoluteLifetime() {
        AuthPolicy policy = AuthPolicy.from(Map.of("idleMinutes", 60 * 24 * 20, "absoluteDays", 2));

        assertThat(policy.idleTimeout()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void anExplicitNullCapMeansUnlimited() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxSessionsAdmin", null);

        assertThat(AuthPolicy.from(config).maxSessionsAdmin()).isNull();
    }

    @Test
    void theSnapshotKeepsNoSecrets() {
        assertThat(AuthPolicy.defaults().snapshot()).containsKeys("accessTtlMinutes", "refreshTtlDays", "idleMinutes", "absoluteDays");
    }
}
