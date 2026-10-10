package com.nimokids.controller;

import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.service.auth.AuthCookies;
import com.nimokids.service.auth.AuthSessionService.IssuedTokens;
import com.nimokids.service.auth.ClientContext;
import com.nimokids.service.auth.ClientContextResolver;
import com.nimokids.service.auth.RateLimiter;
import com.nimokids.service.auth.google.GoogleLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in with Google (docs 06-authentication, 04-API_SPEC A1/A2). Both endpoints are browser navigations, so they
 * answer with redirects. No token ever appears in a URL: after the callback the site obtains its access token through
 * POST /auth/refresh, using the HttpOnly cookie that the callback set.
 */
@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final GoogleLoginService googleLogin;
    private final ClientContextResolver contextResolver;
    private final AuthCookies cookies;
    private final RateLimiter rateLimiter;

    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam(required = false) String returnTo, HttpServletRequest request,
                                      HttpServletResponse response) {
        ClientContext ctx = contextResolver.resolve(request, response);
        limit("google-start:", ctx, 20);
        String authorizationUrl = googleLogin.start(returnTo, ctx);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, authorizationUrl)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        ClientContext ctx = contextResolver.resolve(request, response);
        limit("google-callback:", ctx, 30);
        GoogleLoginService.CallbackResult result = googleLogin.callback(code, state, error, ctx);
        IssuedTokens tokens = result.tokens();
        if (tokens != null && tokens.refreshToken() != null) {
            cookies.setRefresh(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");   // the URL carries a one-time code until it is redirected
        response.sendRedirect(result.redirectUrl());
    }

    private void limit(String prefix, ClientContext ctx, int perMinute) {
        String key = prefix + (ctx.ip() != null ? ctx.ip().getHostAddress() : "unknown");
        if (!rateLimiter.tryAcquire(key, perMinute, Duration.ofMinutes(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", 60));
        }
    }
}
