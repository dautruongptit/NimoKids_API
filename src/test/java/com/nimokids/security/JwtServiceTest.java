package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-with-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8));

    private final JwtService service = new JwtService(new JwtProperties(SECRET, 60));

    @Test
    void issuedTokenRoundTrips() {
        String token = service.generateToken("admin-1", "ADMIN");

        assertThat(service.parse(token)).contains(new JwtPrincipal("admin-1", "ADMIN"));
    }

    @Test
    void expiredAndMalformedTokensAreRejected() {
        assertThat(service.parse(service.generateToken("admin-1", "ADMIN", Duration.ofSeconds(-1)))).isEmpty();
        assertThat(service.parse("not.a.jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }

    @Test
    void roleMustLookLikeARole() {
        assertThat(service.parse(service.generateToken("admin-1", "admin"))).isEmpty();
        assertThat(service.parse(service.generateToken("admin-1", "ROLE ADMIN"))).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("another-secret-key-with-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        String foreign = new JwtService(new JwtProperties(otherSecret, 60)).generateToken("admin-1", "ADMIN");

        assertThat(service.parse(foreign)).isEmpty();
    }

    @Test
    void weakOrInvalidSecretsFailFast() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtService(new JwtProperties(shortSecret, 60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("256 bits");
        assertThatThrownBy(() -> new JwtService(new JwtProperties("%%%not-base64%%%", 60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }
}
