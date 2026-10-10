package com.nimokids.controller;

import com.nimokids.dto.request.LogoutAllRequest;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.SessionInfo;
import com.nimokids.dto.response.SessionStatusResponse;
import com.nimokids.dto.response.SessionSummaryResponse;
import com.nimokids.dto.response.TokenResponse;
import com.nimokids.dto.response.UserInfo;
import com.nimokids.entity.enums.PrincipalType;
import com.nimokids.entity.enums.SessionEndReason;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.security.AdminPrincipal;
import com.nimokids.service.auth.AuthCookies;
import com.nimokids.service.auth.AuthSessionService;
import com.nimokids.service.auth.AuthSessionService.IssuedTokens;
import com.nimokids.service.auth.AuthSessionService.Principal;
import com.nimokids.service.auth.AuthSessionService.RefreshOutcome;
import com.nimokids.service.auth.ClientContext;
import com.nimokids.service.auth.ClientContextResolver;
import com.nimokids.service.auth.RateLimiter;
import com.nimokids.service.auth.google.GoogleLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session and token endpoints (docs 06-authentication, 04-API_SPEC section A). Refresh and logout are authenticated by
 * the HttpOnly refresh cookie (and protected by OriginCsrfFilter); everything else uses the Bearer access token.
 * Tokens are never written to a log or to an error message.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthSessionController {

    private static final int REFRESH_PER_IP_PER_MINUTE = 120;

    private final AuthSessionService sessionService;
    private final ClientContextResolver contextResolver;
    private final AuthCookies cookies;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final GoogleLoginService googleLogin;

    /** A new access token (and a rotated refresh cookie) in exchange for the refresh cookie. */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        ClientContext ctx = contextResolver.resolve(request, response);
        String ipKey = "refresh:ip:" + (ctx.ip() != null ? ctx.ip().getHostAddress() : "unknown");
        if (!rateLimiter.tryAcquire(ipKey, REFRESH_PER_IP_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", 60));
        }
        RefreshOutcome outcome = sessionService.refresh(cookies.readRefresh(request), ctx);
        if (!outcome.ok()) {
            // A dead cookie is removed so the browser stops sending it; a failed refresh never gets a new one.
            cookies.clearRefresh(response);
            throw new BusinessException(outcome.error());
        }
        IssuedTokens tokens = outcome.tokens();
        if (tokens.refreshToken() != null) {
            cookies.setRefresh(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        }
        return ApiResponse.success(toResponse(tokens));
    }

    /** Is there a live session behind the cookie? Never rotates and never fails: the page uses it to pick a screen. */
    @GetMapping("/session")
    public ApiResponse<SessionStatusResponse> session(HttpServletRequest request) {
        return ApiResponse.success(sessionService.peek(cookies.readRefresh(request))
                .flatMap(peeked -> sessionService.describe(peeked.principal()).map(user ->
                        new SessionStatusResponse(true, user, new SessionInfo(peeked.session().getId().toString(),
                                peeked.session().getAbsoluteExpiresAt(), peeked.session().getIdleExpiresAt()), googleLogin.enabled())))
                .orElse(new SessionStatusResponse(false, null, null, googleLogin.enabled())));
    }

    @GetMapping("/me")
    public ApiResponse<SessionStatusResponse> me(@AuthenticationPrincipal AdminPrincipal caller) {
        UserInfo user = sessionService.describe(principalOf(caller)).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.success(new SessionStatusResponse(true, user, null, googleLogin.enabled()));
    }

    /** Ends the current session. Works with the Bearer token or, when it already expired, with the cookie alone. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal caller, HttpServletRequest request,
                                       HttpServletResponse response) {
        if (caller != null && caller.sessionId() != null) {
            sessionService.logout(caller.sessionId(), SessionEndReason.LOGOUT);
        }
        sessionService.logoutByRefreshToken(cookies.readRefresh(request));
        cookies.clearRefresh(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ApiResponse<Map<String, Integer>> logoutAll(@AuthenticationPrincipal AdminPrincipal caller,
                                                       @RequestBody(required = false) LogoutAllRequest body,
                                                       HttpServletRequest request, HttpServletResponse response) {
        ClientContext ctx = contextResolver.resolve(request, response);
        if (!rateLimiter.tryAcquire("logout-all:" + caller.userId(), 10, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", 3600));
        }
        boolean keepCurrent = body != null && body.keepCurrent();
        int revoked = sessionService.logoutAll(principalOf(caller), keepCurrent ? caller.sessionId() : null, ctx);
        if (!keepCurrent) {
            cookies.clearRefresh(response);
        }
        return ApiResponse.success(Map.of("revoked", revoked));
    }

    /** The caller's own sessions. The id in the query is never trusted: the list is built from the token's principal. */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummaryResponse>> sessions(@AuthenticationPrincipal AdminPrincipal caller) {
        return ApiResponse.success(sessionService.listOwn(principalOf(caller), caller.sessionId()).stream()
                .map(v -> new SessionSummaryResponse(v.id().toString(), v.current(), v.createdAt(), v.lastSeenAt(),
                        v.absoluteExpiresAt(), v.ipMasked(), v.browser(), v.os(), v.deviceType()))
                .toList());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID sessionId,
                                       HttpServletRequest request, HttpServletResponse response) {
        ClientContext ctx = contextResolver.resolve(request, response);
        // Same 404 for "does not exist" and "belongs to someone else": the id of another account is never confirmed.
        if (!sessionService.revokeOwn(principalOf(caller), sessionId, ctx)) {
            throw new BusinessException(ErrorCode.AUTH_SESSION_NOT_FOUND);
        }
        if (sessionId.equals(caller.sessionId())) {
            cookies.clearRefresh(response);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ------------------------------------------------------------------------------------------------

    private TokenResponse toResponse(IssuedTokens tokens) {
        long expiresIn = Math.max(0, Duration.between(clock.instant(), tokens.accessExpiresAt()).getSeconds());
        UserInfo user = sessionService.describe(tokens.principal()).orElse(null);
        SessionInfo session = new SessionInfo(tokens.session().getId().toString(), tokens.session().getAbsoluteExpiresAt(),
                tokens.session().getIdleExpiresAt());
        return new TokenResponse(tokens.accessToken(), "Bearer", expiresIn, session, user);
    }

    private static Principal principalOf(AdminPrincipal caller) {
        PrincipalType type = caller.isUser() ? PrincipalType.USER : PrincipalType.ADMIN;
        return new Principal(type, UUID.fromString(caller.userId()), caller.role());
    }
}
