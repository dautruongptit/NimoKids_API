package com.nimokids.service.auth.google;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.JwsHeader;
import java.security.Key;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google ID token: RS256 signature against Google's published keys, issuer, audience (our client id), expiry
 * (60 s clock skew) and the presence of {@code sub}. Nonce and e-mail verification are checked by the caller, which
 * knows the attempt. Only RS256 is accepted, so a token cannot choose a weaker algorithm.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    /** What the application needs from the token. */
    public record Identity(String subject, String email, boolean emailVerified, String name, String picture,
                           String locale, String nonce) {
    }

    private final GoogleOidcProperties properties;
    private final GoogleJwks jwks;

    public GoogleIdTokenVerifier(GoogleOidcProperties properties, GoogleJwks jwks) {
        this.properties = properties;
        this.jwks = jwks;
    }

    public Optional<Identity> verify(String idToken) {
        try {
            Claims claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            if (!"RS256".equals(header.getAlgorithm())) {
                                throw new JwtException("Only RS256 is accepted");
                            }
                            Key key = jwks.key(header.getKeyId());
                            if (key == null) {
                                throw new JwtException("Unknown signing key");
                            }
                            return key;
                        }
                    })
                    .clockSkewSeconds(60)
                    .requireAudience(properties.clientId())
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
            if (claims.getIssuer() == null || !ISSUERS.contains(claims.getIssuer())) {
                return Optional.empty();
            }
            if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getIssuedAt() == null) {
                return Optional.empty();
            }
            Object verified = claims.get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
            return Optional.of(new Identity(
                    claims.getSubject(), claims.get("email", String.class), emailVerified, claims.get("name", String.class),
                    claims.get("picture", String.class), claims.get("locale", String.class), claims.get("nonce", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected Google ID token: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
