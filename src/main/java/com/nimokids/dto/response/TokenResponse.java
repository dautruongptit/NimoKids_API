package com.nimokids.dto.response;

/** The answer of POST /auth/refresh. The refresh token travels in an HttpOnly cookie, never in this body. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, SessionInfo session, UserInfo user) {
}
