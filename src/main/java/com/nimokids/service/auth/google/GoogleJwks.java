package com.nimokids.service.auth.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Google's public signing keys (JWKS), fetched over HTTPS and cached. An unknown key id triggers ONE refresh (at most
 * once a minute), which is how Google's key rotation is followed without hammering the endpoint.
 */
@Slf4j
@Component
public class GoogleJwks {

    private static final Duration CACHE = Duration.ofHours(1);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final GoogleOidcProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http;

    private volatile Map<String, Key> keys = Map.of();
    private volatile Instant loadedAt = Instant.MIN;
    private volatile Instant lastAttempt = Instant.MIN;

    public GoogleJwks(GoogleOidcProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, properties.httpTimeoutSeconds()))).build();
    }

    /** The RSA public key for this key id, or null when Google does not publish it. */
    public Key key(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return null;
        }
        Instant now = clock.instant();
        Key known = keys.get(keyId);
        boolean stale = now.isAfter(loadedAt.plus(CACHE));
        if ((known == null || stale) && now.isAfter(lastAttempt.plus(MIN_REFRESH_INTERVAL))) {
            refresh(now);
            known = keys.get(keyId);
        }
        return known;
    }

    private synchronized void refresh(Instant now) {
        lastAttempt = now;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.jwksUri()))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.httpTimeoutSeconds()))).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google JWKS answered HTTP {}", response.statusCode());
                return;
            }
            Map<String, Key> loaded = new HashMap<>();
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode jwk : root.path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText()) || jwk.path("kid").asText().isBlank()) {
                    continue;
                }
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
                loaded.put(jwk.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            }
            if (!loaded.isEmpty()) {
                keys = Map.copyOf(loaded);
                loadedAt = now;
            }
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            log.warn("Could not load Google signing keys: {}", ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
