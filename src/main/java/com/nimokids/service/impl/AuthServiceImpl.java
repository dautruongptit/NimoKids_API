package com.nimokids.service.impl;

import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.LoginResponse;
import com.nimokids.entity.AdminUser;
import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.EventSeverity;
import com.nimokids.entity.enums.LoginOutcome;
import com.nimokids.entity.enums.PrincipalType;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AdminUserRepository;
import com.nimokids.service.AuthService;
import com.nimokids.service.auth.AuthPolicyService;
import com.nimokids.service.auth.AuthSessionService;
import com.nimokids.service.auth.ClientContext;
import com.nimokids.service.auth.IpMasker;
import com.nimokids.service.auth.LoginHistoryService;
import com.nimokids.service.auth.RateLimiter;
import com.nimokids.service.auth.SecurityEventService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";
    /** After this many consecutive failures the account is locked, for 1, 2, 4 ... up to 60 minutes. */
    private static final int LOCK_AFTER_FAILURES = 5;
    private static final int MAX_LOCK_MINUTES = 60;
    private static final int MAX_ATTEMPTS_PER_IP = 30;
    private static final Duration IP_WINDOW = Duration.ofMinutes(15);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessionService;
    private final AuthPolicyService policyService;
    private final LoginHistoryService loginHistory;
    private final SecurityEventService events;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    /** Compared against when the account does not exist, so unknown emails cost the same time as wrong passwords. */
    private final String dummyHash;

    public AuthServiceImpl(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
                           AuthSessionService sessionService, AuthPolicyService policyService, LoginHistoryService loginHistory,
                           SecurityEventService events, RateLimiter rateLimiter, Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.policyService = policyService;
        this.loginHistory = loginHistory;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    // A failed login must keep its counters even though it ends in an exception.
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginRequest request, ClientContext ctx) {
        Instant now = clock.instant();
        String email = request.email().trim();
        String hint = IpMasker.maskEmail(email);
        String ipKey = "login:ip:" + (ctx.ip() != null ? ctx.ip().getHostAddress() : "unknown");

        if (!rateLimiter.tryAcquire(ipKey, MAX_ATTEMPTS_PER_IP, IP_WINDOW)) {
            loginHistory.record(null, null, null, AuthMethod.PASSWORD, LoginOutcome.BLOCKED, "RATE_LIMITED", ctx, hint);
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", IP_WINDOW.toSeconds()));
        }

        AdminUser admin = adminUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (admin != null && admin.getLockedUntil() != null && now.isBefore(admin.getLockedUntil())) {
            loginHistory.record(null, admin.getId(), null, AuthMethod.PASSWORD, LoginOutcome.BLOCKED, "LOCKED_OUT", ctx, hint);
            long retry = Duration.between(now, admin.getLockedUntil()).getSeconds();
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", Math.max(1, retry)));
        }

        boolean active = admin != null && admin.isActive();
        boolean passwordMatches = passwordEncoder.matches(request.password(), active ? admin.getPasswordHash() : dummyHash);
        if (!active || !passwordMatches) {
            registerFailure(admin, ctx, hint, now);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        admin.setFailedLoginCount((short) 0);
        admin.setLockedUntil(null);
        admin.setLastLoginAt(now);
        adminUserRepository.save(admin);

        AuthSessionService.IssuedTokens issued = sessionService.createSession(
                new AuthSessionService.Principal(PrincipalType.ADMIN, admin.getId(), admin.getRole().name()),
                AuthMethod.PASSWORD, ctx);
        LoginHistoryService.Recorded recorded = loginHistory.record(null, admin.getId(), issued.session().getId(),
                AuthMethod.PASSWORD, LoginOutcome.SUCCESS, null, ctx, null);
        issued.session().setLoginHistoryId(recorded.id());
        if (policyService.current().detectNewDevice() && recorded.newDevice()) {
            events.security("NEW_DEVICE_LOGIN", EventSeverity.INFO, issued.session().getId(), null, admin.getId(), ctx,
                    Map.of("newIp", recorded.newIp()));
        }
        log.info("Admin {} logged in with role {}", admin.getId(), admin.getRole());

        long expiresIn = Math.max(0, Duration.between(now, issued.accessExpiresAt()).getSeconds());
        LoginResponse response = new LoginResponse(issued.accessToken(), "Bearer", expiresIn, admin.getEmail(), admin.getRole());
        return new LoginResult(response, issued.refreshToken(), issued.refreshExpiresAt());
    }

    private void registerFailure(AdminUser admin, ClientContext ctx, String hint, Instant now) {
        loginHistory.record(null, admin != null ? admin.getId() : null, null, AuthMethod.PASSWORD, LoginOutcome.FAILED,
                "BAD_CREDENTIALS", ctx, hint);
        log.warn("Failed login attempt");
        if (admin == null || !admin.isActive()) {
            return;
        }
        int failures = admin.getFailedLoginCount() + 1;
        admin.setFailedLoginCount((short) Math.min(failures, Short.MAX_VALUE));
        if (failures >= LOCK_AFTER_FAILURES) {
            long minutes = Math.min(MAX_LOCK_MINUTES, 1L << Math.min(failures - LOCK_AFTER_FAILURES, 6));
            admin.setLockedUntil(now.plus(Duration.ofMinutes(minutes)));
            events.security("ADMIN_LOGIN_LOCKED", EventSeverity.WARN, null, null, admin.getId(), ctx,
                    Map.of("failures", failures, "lockedMinutes", minutes));
        }
        adminUserRepository.save(admin);
    }
}
