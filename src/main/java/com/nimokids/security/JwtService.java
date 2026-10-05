package com.nimokids.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies signed JWTs (HS256). Player APIs are anonymous and never use this; it protects the
 * future Admin portal. Tokens are never logged.
 */
@Slf4j
@Component
public class JwtService {

    static final String ISSUER = "nimokids-api";
    private static final String ROLE_CLAIM = "role";
    private static final Pattern ROLE_FORMAT = Pattern.compile("^[A-Z][A-Z_]{1,29}$");
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration defaultTtl;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(properties.secret());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("app.jwt.secret must be a Base64 encoded string", ex);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.defaultTtl = Duration.ofMinutes(properties.expirationMinutes());
    }

    public long getExpiresInSeconds() {
        return defaultTtl.toSeconds();
    }

    public String generateToken(String subject, String role) {
        return generateToken(subject, role, defaultTtl);
    }

    public String generateToken(String subject, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry. Returns empty for anything invalid (unsigned, tampered, expired,
     * wrong issuer, missing or malformed subject/role) so callers cannot tell the reasons apart.
     */
    public Optional<JwtPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            if (claims.getSubject() == null || role == null || !ROLE_FORMAT.matcher(role).matches()) {
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
