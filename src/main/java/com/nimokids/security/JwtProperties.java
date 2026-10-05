package com.nimokids.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The secret has no default outside the dev profile, so a production start-up without
 * JWT_SECRET fails fast instead of silently using a known key.
 *
 * @param secret            Base64-encoded HMAC key, at least 256 bits
 * @param expirationMinutes lifetime of issued tokens
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @DefaultValue("60") @Positive long expirationMinutes) {
}
