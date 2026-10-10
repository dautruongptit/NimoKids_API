package com.nimokids.security;

import java.util.UUID;

/**
 * Identity extracted from a verified token.
 *
 * @param type      ADMIN or USER (claim typ); null for a token issued before sessions existed (treated as ADMIN)
 * @param sessionId the login session (claim sid); null for such a legacy token
 */
public record JwtPrincipal(String subject, String role, String type, UUID sessionId) {

    /** Legacy tokens: no type and no session. */
    public JwtPrincipal(String subject, String role) {
        this(subject, role, null, null);
    }
}
