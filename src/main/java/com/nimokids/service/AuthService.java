package com.nimokids.service;

import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.LoginResponse;

public interface AuthService {

    /**
     * Verifies the credentials and issues a JWT with the user's role claim.
     * Any failure (unknown user, inactive user, wrong password) gives the same UNAUTHORIZED error.
     */
    LoginResponse login(LoginRequest request);
}
