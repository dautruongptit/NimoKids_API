package com.nimokids.security;

/** The authenticated admin, as read from a verified JWT. Available in controllers via @AuthenticationPrincipal. */
public record AdminPrincipal(String userId, String role) {
}
