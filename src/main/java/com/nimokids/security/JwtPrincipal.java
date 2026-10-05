package com.nimokids.security;

/** Identity extracted from a verified token. */
public record JwtPrincipal(String subject, String role) {
}
