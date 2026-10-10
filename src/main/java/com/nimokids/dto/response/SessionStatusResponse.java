package com.nimokids.dto.response;

/**
 * GET /auth/session: lets the page choose between "show login" and "refresh" without error noise.
 * {@code googleEnabled} tells whether the optional Google sign-in is configured, so the page shows its button only then.
 */
public record SessionStatusResponse(boolean authenticated, UserInfo user, SessionInfo session, boolean googleEnabled) {
}
