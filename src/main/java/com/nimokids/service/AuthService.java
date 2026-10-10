package com.nimokids.service;

import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.LoginResponse;
import com.nimokids.service.auth.ClientContext;
import java.time.Instant;

public interface AuthService {

    /** The answer body plus the refresh token, which the controller puts in an HttpOnly cookie. */
    record LoginResult(LoginResponse response, String refreshToken, Instant refreshExpiresAt) {
    }

    /**
     * Verifies the credentials, opens a session and issues an access token (role and session id in its claims) and a
     * rotating refresh token. Any failure (unknown user, inactive user, wrong password) gives the same UNAUTHORIZED
     * error. Repeated failures lock the account progressively (RATE_LIMITED). Every attempt is written to the login
     * history.
     */
    LoginResult login(LoginRequest request, ClientContext context);
}
