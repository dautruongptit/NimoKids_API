package com.nimokids.security;

import java.util.UUID;

/**
 * The authenticated caller, as read from a verified JWT. Available in controllers via @AuthenticationPrincipal.
 * Despite the name it is also used for end users (role USER, type USER): admin endpoints refuse the USER type.
 */
public record AdminPrincipal(String userId, String role, String type, UUID sessionId) {

    public AdminPrincipal(String userId, String role) {
        this(userId, role, null, null);
    }

    public boolean isUser() {
        return "USER".equals(type);
    }
}
