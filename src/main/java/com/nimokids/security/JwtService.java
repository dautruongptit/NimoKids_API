package com.nimokids.security;

import java.util.UUID;
import io.jsonwebtoken.ExpiredJwtException;
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
    private static final String TYPE_CLAIM = "typ";
    private static final String SESSION_CLAIM = "sid";
    private static final Pattern ROLE_FORMAT = Pattern.compile("^[A-Z][A-Z_]{1,29}$");
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration defaultTtl;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(properties.secret());
        } catch (RuntimeException ex) {
            // An unset JWT_SECRET reaches us as the literal text "${JWT_SECRET}", which is not valid Base64.
            throw new IllegalStateException(
                    "app.jwt.secret must be a Base64 encoded string (is the JWT_SECRET environment variable set?)", ex);
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

    /**
     * A session-bound access token: carries the principal type, the session id (sid) and a unique id (jti).
     * The caller supplies both instants so lifetimes follow the application clock.
     */
    public String generateAccessToken(String subject, String role, String type, UUID sessionId,
                                      Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim(ROLE_CLAIM, role)
                .claim(TYPE_CLAIM, type)
                .claim(SESSION_CLAIM, sessionId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
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
        return parseDetailed(token).principal();
    }

    /** Why a token was refused, for the client: EXPIRED asks it to refresh, INVALID asks it to sign in again. */
    public enum Status { VALID, EXPIRED, INVALID }

    public record ParseResult(Status status, Optional<JwtPrincipal> principal) {
    }

    public ParseResult parseDetailed(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            if (claims.getSubject() == null || role == null || !ROLE_FORMAT.matcher(role).matches()) {
                return new ParseResult(Status.INVALID, Optional.empty());
            }
            String type = claims.get(TYPE_CLAIM, String.class);
            UUID sessionId = null;
            String sid = claims.get(SESSION_CLAIM, String.class);
            if (sid != null) {
                try {
                    sessionId = UUID.fromString(sid);
                } catch (IllegalArgumentException ex) {
                    return new ParseResult(Status.INVALID, Optional.empty());
                }
            }
            if (type != null && !type.equals("ADMIN") && !type.equals("USER")) {
                return new ParseResult(Status.INVALID, Optional.empty());
            }
            return new ParseResult(Status.VALID, Optional.of(new JwtPrincipal(claims.getSubject(), role, type, sessionId)));
        } catch (ExpiredJwtException ex) {
            return new ParseResult(Status.EXPIRED, Optional.empty());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return new ParseResult(Status.INVALID, Optional.empty());
        }
    }
}
