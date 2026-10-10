package com.nimokids.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.nimokids.service.auth.ClientContextResolver;
import com.nimokids.service.auth.AuthCookies;
import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.LoginResponse;
import com.nimokids.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientContextResolver contextResolver;
    private final AuthCookies cookies;

    /** Admin password login. Same body as before; the refresh token is set as an HttpOnly cookie. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request, contextResolver.resolve(http, response));
        cookies.setRefresh(response, result.refreshToken(), result.refreshExpiresAt());
        return ApiResponse.success("Login successful", result.response());
    }
}
