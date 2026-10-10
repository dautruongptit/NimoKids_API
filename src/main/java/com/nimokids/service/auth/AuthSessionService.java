package com.nimokids.service.auth;

import com.nimokids.entity.AdminUser;
import com.nimokids.entity.AuthSession;
import com.nimokids.entity.RefreshToken;
import com.nimokids.entity.User;
import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.AuthSessionStatus;
import com.nimokids.entity.enums.EventActorType;
import com.nimokids.entity.enums.EventResult;
import com.nimokids.entity.enums.EventSeverity;
import com.nimokids.entity.enums.PrincipalType;
import com.nimokids.entity.enums.RefreshTokenStatus;
import com.nimokids.entity.enums.SessionEndReason;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AdminUserRepository;
import com.nimokids.repository.AuthSessionRepository;
import com.nimokids.repository.RefreshTokenRepository;
import com.nimokids.repository.UserRepository;
import com.nimokids.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Login sessions and refresh tokens (docs 06-authentication). One session = one refresh-token family.
 *
 * Rules that this class enforces:
 * <ul>
 *   <li>the absolute lifetime is fixed at login and never extended by refreshing</li>
 *   <li>a refresh token works once; a second use inside the grace window is a benign race (two tabs), after the
 *       window it is theft and ends the session</li>
 *   <li>revocation is checked on every request through {@link SessionGuard}, so it works before the access token expires</li>
 *   <li>an IP change never ends a session by itself</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService implements SessionGuard {

    /** The account a session belongs to. */
    public record Principal(PrincipalType type, UUID id, String role) {
    }

    /** What the client receives. {@code refreshToken} is null when the browser already holds the current one. */
    public record IssuedTokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt,
                               AuthSession session, Principal principal) {
    }

    /** A live session found behind a refresh cookie, without rotating anything. */
    public record Peeked(AuthSession session, Principal principal) {
    }

    public record RefreshOutcome(IssuedTokens tokens, ErrorCode error) {
        public boolean ok() {
            return tokens != null;
        }
    }

    public record SessionView(UUID id, boolean current, AuthSessionStatus status, Instant createdAt, Instant lastSeenAt,
                              Instant idleExpiresAt, Instant absoluteExpiresAt, String ipMasked, String browser, String os,
                              String deviceType) {
    }

    /** What the guard remembers about a session between two database reads. */
    private record Cached(AuthSessionStatus status, Instant idleExpiresAt, Instant absoluteExpiresAt, InetAddress lastIp,
                          Instant loadedAt) {
    }

    private static final Duration GUARD_CACHE_TTL = Duration.ofSeconds(15);
    private static final Duration IP_EVENT_THROTTLE = Duration.ofMinutes(5);

    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final AdminUserRepository admins;
    private final UserRepository users;
    private final JwtService jwtService;
    private final AuthPolicyService policyService;
    private final SecurityEventService events;
    private final ClientIpResolver ipResolver;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    private final Map<UUID, Cached> guardCache = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastTouch = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastIpEvent = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------------------------------------ create

    /** Opens a session after a successful sign-in and issues its first access and refresh tokens. */
    @Transactional
    public IssuedTokens createSession(Principal principal, AuthMethod method, ClientContext ctx) {
        AuthPolicy policy = policyService.current();
        Instant now = clock.instant();
        Instant absolute = now.plus(policy.absoluteLifetime());
        Instant idle = earlier(now.plus(policy.idleTimeout()), absolute);

        enforceSessionCap(principal, policy, ctx);

        AuthSession session = sessions.save(AuthSession.builder()
                .userId(principal.type() == PrincipalType.USER ? principal.id() : null)
                .adminUserId(principal.type() == PrincipalType.ADMIN ? principal.id() : null)
                .authMethod(method)
                .status(AuthSessionStatus.ACTIVE)
                .createdAt(now)
                .lastSeenAt(now)
                .idleExpiresAt(idle)
                .absoluteExpiresAt(absolute)
                .loginIp(ctx.ip())
                .lastIp(ctx.ip())
                .userAgentLogin(ctx.userAgent())
                .userAgentLast(ctx.userAgent())
                .browser(ctx.browser())
                .os(ctx.os())
                .deviceType(ctx.deviceType())
                .deviceIdHash(ctx.deviceIdHash())
                .policySnapshot(new HashMap<>(policy.snapshot()))
                .updatedAt(now)
                .build());

        String raw = TokenHasher.newRawToken();
        Instant refreshExpires = earlier(now.plus(policy.refreshTtl()), absolute);
        tokens.save(newToken(session, 1, raw, now, refreshExpires, ctx.ip()));
        return issue(session, principal, raw, refreshExpires, policy, now);
    }

    private void enforceSessionCap(Principal principal, AuthPolicy policy, ClientContext ctx) {
        Integer cap = principal.type() == PrincipalType.ADMIN ? policy.maxSessionsAdmin() : policy.maxSessionsUser();
        if (cap == null) {
            return;
        }
        List<AuthSession> active = principal.type() == PrincipalType.ADMIN
                ? sessions.findByAdminUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE)
                : sessions.findByUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE);
        int toEnd = active.size() - cap + 1;          // room for the new one
        for (int i = 0; i < toEnd && i < active.size(); i++) {
            AuthSession oldest = active.get(i);
            end(oldest, AuthSessionStatus.REVOKED, SessionEndReason.EVICTED, null);
            events.security("SESSION_EVICTED", EventSeverity.INFO, oldest.getId(),
                    principal.type() == PrincipalType.USER ? principal.id() : null,
                    principal.type() == PrincipalType.ADMIN ? principal.id() : null, ctx, Map.of("cap", cap));
        }
    }

    // ------------------------------------------------------------------------------------------------ refresh

    /**
     * The refresh decision table of 02-FLOWS section 4. Failures are returned, not thrown, so that ending a session
     * after a reuse is committed instead of rolled back.
     */
    @Transactional
    public RefreshOutcome refresh(String rawToken, ClientContext ctx) {
        if (!TokenHasher.looksValid(rawToken)) {
            return failure(ErrorCode.REFRESH_INVALID);
        }
        RefreshToken presented = tokens.findByHashForUpdate(TokenHasher.sha256(rawToken)).orElse(null);
        if (presented == null) {
            return failure(ErrorCode.REFRESH_INVALID);
        }
        AuthSession session = presented.getSession();
        AuthPolicy policy = policyService.current();
        Instant now = clock.instant();

        if (session.getStatus() == AuthSessionStatus.REVOKED) {
            return failure(ErrorCode.SESSION_REVOKED);
        }
        if (session.getStatus() == AuthSessionStatus.EXPIRED) {
            return failure(ErrorCode.SESSION_EXPIRED);
        }
        if (now.isAfter(session.getAbsoluteExpiresAt())) {
            end(session, AuthSessionStatus.EXPIRED, SessionEndReason.ABSOLUTE, null);
            return failure(ErrorCode.SESSION_EXPIRED);
        }
        if (now.isAfter(session.getIdleExpiresAt())) {
            end(session, AuthSessionStatus.EXPIRED, SessionEndReason.IDLE, null);
            return failure(ErrorCode.SESSION_EXPIRED);
        }
        Optional<Principal> principal = resolvePrincipal(session);
        if (principal.isEmpty()) {
            end(session, AuthSessionStatus.REVOKED, SessionEndReason.ACCOUNT_DISABLED, null);
            events.security("ACCOUNT_DISABLED", EventSeverity.WARN, session.getId(), session.getUserId(),
                    session.getAdminUserId(), ctx, Map.of("where", "refresh"));
            return failure(ErrorCode.ACCOUNT_DISABLED);
        }

        switch (presented.getStatus()) {
            case ACTIVE -> {
                if (now.isAfter(presented.getExpiresAt())) {
                    presented.setStatus(RefreshTokenStatus.EXPIRED);
                    end(session, AuthSessionStatus.EXPIRED, SessionEndReason.IDLE, null);
                    return failure(ErrorCode.SESSION_EXPIRED);
                }
                return rotate(presented, session, principal.get(), policy, now, ctx);
            }
            case ROTATED -> {
                RefreshToken successor = presented.getReplacedById() == null ? null
                        : tokens.findById(presented.getReplacedById()).orElse(null);
                boolean benignRace = presented.getUsedAt() != null
                        && !now.isAfter(presented.getUsedAt().plus(policy.reuseGrace()))
                        && successor != null && successor.getStatus() == RefreshTokenStatus.ACTIVE && successor.getUsedAt() == null;
                if (benignRace) {
                    events.security("REFRESH_RACE", EventSeverity.INFO, session.getId(), session.getUserId(),
                            session.getAdminUserId(), ctx, Map.of());
                    // The browser already holds (or is about to receive) the successor: only a new access token is issued.
                    return new RefreshOutcome(issue(session, principal.get(), null, successor.getExpiresAt(), policy, now), null);
                }
                end(session, AuthSessionStatus.REVOKED, SessionEndReason.REFRESH_REUSE, null);
                events.security("REFRESH_REUSE_DETECTED", EventSeverity.CRITICAL, session.getId(), session.getUserId(),
                        session.getAdminUserId(), ctx, Map.of("generation", presented.getGeneration()));
                return failure(ErrorCode.REFRESH_REUSED);
            }
            default -> {
                return failure(ErrorCode.REFRESH_INVALID);
            }
        }
    }

    private RefreshOutcome rotate(RefreshToken presented, AuthSession session, Principal principal, AuthPolicy policy,
                                  Instant now, ClientContext ctx) {
        if (!policy.refreshRotation()) {
            touchSession(session, now, ctx, policy);
            return new RefreshOutcome(issue(session, principal, null, presented.getExpiresAt(), policy, now), null);
        }
        // The old token must stop being ACTIVE before the new one is inserted (one ACTIVE token per session).
        presented.setStatus(RefreshTokenStatus.ROTATED);
        presented.setUsedAt(now);
        tokens.saveAndFlush(presented);

        String raw = TokenHasher.newRawToken();
        Instant expires = earlier(now.plus(policy.refreshTtl()), session.getAbsoluteExpiresAt());
        RefreshToken next = tokens.save(newToken(session, presented.getGeneration() + 1, raw, now, expires, ctx.ip()));
        presented.setReplacedById(next.getId());
        tokens.save(presented);

        session.setLastRefreshedAt(now);
        touchSession(session, now, ctx, policy);
        return new RefreshOutcome(issue(session, principal, raw, expires, policy, now), null);
    }

    /** Moves the idle deadline forward (never past the absolute one) and records IP / user-agent changes. */
    private void touchSession(AuthSession session, Instant now, ClientContext ctx, AuthPolicy policy) {
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(earlier(now.plus(policy.idleTimeout()), session.getAbsoluteExpiresAt()));
        if (ctx.userAgent() != null) {
            session.setUserAgentLast(ctx.userAgent());
        }
        if (ctx.ip() != null && !ctx.ip().equals(session.getLastIp())) {
            session.setLastIp(ctx.ip());
            session.setIpChangeCount(session.getIpChangeCount() + 1);
            events.security("IP_CHANGED", EventSeverity.INFO, session.getId(), session.getUserId(), session.getAdminUserId(),
                    ctx, Map.of("changes", session.getIpChangeCount()));
        }
        session.setUpdatedAt(now);
        sessions.save(session);
        guardCache.remove(session.getId());
    }

    // ------------------------------------------------------------------------------------------------ end

    /** Ends the session of the given id (logout). Idempotent; returns false when there is no such session. */
    @Transactional
    public boolean logout(UUID sessionId, SessionEndReason reason) {
        return sessions.findById(sessionId).map(session -> {
            if (session.getStatus() == AuthSessionStatus.ACTIVE) {
                end(session, AuthSessionStatus.REVOKED, reason, null);
            }
            return true;
        }).orElse(false);
    }

    /** Ends the session that owns this refresh cookie (logout when the access token is already gone). */
    @Transactional
    public void logoutByRefreshToken(String rawToken) {
        if (!TokenHasher.looksValid(rawToken)) {
            return;
        }
        tokens.findByHashForUpdate(TokenHasher.sha256(rawToken)).ifPresent(token -> {
            AuthSession session = token.getSession();
            if (session.getStatus() == AuthSessionStatus.ACTIVE) {
                end(session, AuthSessionStatus.REVOKED, SessionEndReason.LOGOUT, null);
            }
        });
    }

    /** Ends every active session of the principal, except {@code keep}. Returns how many were ended. */
    @Transactional
    public int logoutAll(Principal principal, UUID keep, ClientContext ctx) {
        List<AuthSession> active = principal.type() == PrincipalType.ADMIN
                ? sessions.findByAdminUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE)
                : sessions.findByUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE);
        int ended = 0;
        for (AuthSession session : active) {
            if (session.getId().equals(keep)) {
                continue;
            }
            end(session, AuthSessionStatus.REVOKED, SessionEndReason.LOGOUT_ALL, null);
            ended++;
        }
        events.audit("SESSIONS_REVOKED_ALL", actor(principal), principal.id(),
                principal.type() == PrincipalType.USER ? principal.id() : null,
                principal.type() == PrincipalType.ADMIN ? principal.id() : null, keep, ctx, EventResult.SUCCESS,
                Map.of("revoked", ended));
        return ended;
    }

    /** Ends one session owned by the principal. False when it does not exist or belongs to someone else. */
    @Transactional
    public boolean revokeOwn(Principal principal, UUID sessionId, ClientContext ctx) {
        Optional<AuthSession> found = sessions.findById(sessionId).filter(s -> principal.id().equals(s.principalId())
                && (principal.type() == PrincipalType.ADMIN) == s.isAdminSession());
        if (found.isEmpty()) {
            return false;
        }
        AuthSession session = found.get();
        if (session.getStatus() == AuthSessionStatus.ACTIVE) {
            end(session, AuthSessionStatus.REVOKED, SessionEndReason.LOGOUT, null);
            events.audit("SESSION_REVOKED", actor(principal), principal.id(),
                    principal.type() == PrincipalType.USER ? principal.id() : null,
                    principal.type() == PrincipalType.ADMIN ? principal.id() : null, session.getId(), ctx,
                    EventResult.SUCCESS, Map.of());
        }
        return true;
    }

    /** The principal's own active sessions (never anyone else's). */
    @Transactional(readOnly = true)
    public List<SessionView> listOwn(Principal principal, UUID currentSessionId) {
        List<AuthSession> active = principal.type() == PrincipalType.ADMIN
                ? sessions.findByAdminUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE)
                : sessions.findByUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE);
        Instant now = clock.instant();
        return active.stream()
                .filter(s -> now.isBefore(s.getAbsoluteExpiresAt()) && now.isBefore(s.getIdleExpiresAt()))
                .map(s -> new SessionView(s.getId(), s.getId().equals(currentSessionId), s.getStatus(), s.getCreatedAt(),
                        s.getLastSeenAt(), s.getIdleExpiresAt(), s.getAbsoluteExpiresAt(), IpMasker.mask(s.getLastIp()),
                        s.getBrowser(), s.getOs(), s.getDeviceType()))
                .toList();
    }

    /** Ends every session of an account that has just been disabled. */
    @Transactional
    public int revokeAllOf(Principal principal, SessionEndReason reason, UUID byAdminId) {
        List<AuthSession> active = principal.type() == PrincipalType.ADMIN
                ? sessions.findByAdminUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE)
                : sessions.findByUserIdAndStatusOrderByCreatedAtAsc(principal.id(), AuthSessionStatus.ACTIVE);
        active.forEach(s -> end(s, AuthSessionStatus.REVOKED, reason, byAdminId));
        return active.size();
    }

    private void end(AuthSession session, AuthSessionStatus status, SessionEndReason reason, UUID byAdminId) {
        Instant now = clock.instant();
        session.setStatus(status);
        session.setEndedAt(now);
        session.setEndedReason(reason);
        session.setRevokedByAdminId(byAdminId);
        session.setUpdatedAt(now);
        sessions.save(session);
        tokens.revokeActiveOfSession(session.getId());
        guardCache.remove(session.getId());
        lastTouch.remove(session.getId());
    }

    // ------------------------------------------------------------------------------------------------ guard (every request)

    @Override
    public Result check(UUID sessionId, HttpServletRequest request) {
        Instant now = clock.instant();
        Cached state = guardCache.get(sessionId);
        if (state == null || now.isAfter(state.loadedAt().plus(GUARD_CACHE_TTL))) {
            state = sessions.findById(sessionId)
                    .map(s -> new Cached(s.getStatus(), s.getIdleExpiresAt(), s.getAbsoluteExpiresAt(), s.getLastIp(), now))
                    .orElse(null);
            if (state == null) {
                guardCache.remove(sessionId);
                return Result.REVOKED;
            }
            guardCache.put(sessionId, state);
        }
        if (state.status() == AuthSessionStatus.REVOKED) {
            return Result.REVOKED;
        }
        if (state.status() == AuthSessionStatus.EXPIRED || now.isAfter(state.absoluteExpiresAt()) || now.isAfter(state.idleExpiresAt())) {
            return Result.EXPIRED;
        }
        recordActivity(sessionId, state, request, now);
        return Result.OK;
    }

    /** At most one write per throttle window, never one per request. */
    private void recordActivity(UUID sessionId, Cached state, HttpServletRequest request, Instant now) {
        AuthPolicy policy = policyService.current();
        Instant previous = lastTouch.get(sessionId);
        if (previous != null && now.isBefore(previous.plus(policy.lastSeenThrottle()))) {
            return;
        }
        lastTouch.put(sessionId, now);
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                Instant idle = earlier(now.plus(policy.idleTimeout()), state.absoluteExpiresAt());
                sessions.touch(sessionId, now, idle, now.minus(policy.lastSeenThrottle()));
                InetAddress ip = ipResolver.resolve(request);
                if (ip != null && !ip.equals(state.lastIp())) {
                    Instant lastEvent = lastIpEvent.get(sessionId);
                    if (lastEvent == null || now.isAfter(lastEvent.plus(IP_EVENT_THROTTLE))) {
                        lastIpEvent.put(sessionId, now);
                        sessions.findById(sessionId).ifPresent(s -> {
                            s.setLastIp(ip);
                            s.setIpChangeCount(s.getIpChangeCount() + 1);
                            s.setUpdatedAt(now);
                            sessions.save(s);
                            events.security("IP_CHANGED", EventSeverity.INFO, sessionId, s.getUserId(), s.getAdminUserId(),
                                    new ClientContext(ip, null, null, null, null, null, null), Map.of("changes", s.getIpChangeCount()));
                        });
                    }
                }
            });
            guardCache.remove(sessionId);
        } catch (RuntimeException ex) {
            log.debug("Could not record session activity: {}", ex.getClass().getSimpleName());
        }
    }

    /** Who a principal is, for the responses (no secrets). */
    @Transactional(readOnly = true)
    public Optional<com.nimokids.dto.response.UserInfo> describe(Principal principal) {
        if (principal.type() == PrincipalType.ADMIN) {
            return admins.findById(principal.id()).map(a -> new com.nimokids.dto.response.UserInfo(
                    a.getId().toString(), "ADMIN", a.getRole().name(), a.getEmail(), null, null));
        }
        return users.findById(principal.id()).map(u -> new com.nimokids.dto.response.UserInfo(
                u.getId().toString(), "USER", "USER", u.getEmail(), u.getDisplayName(), u.getAvatarUrl()));
    }

    /** Read-only look at a refresh cookie: is there a live session behind it? (GET /auth/session; nothing is rotated) */
    @Transactional(readOnly = true)
    public Optional<Peeked> peek(String rawToken) {
        if (!TokenHasher.looksValid(rawToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .filter(t -> t.getStatus() == RefreshTokenStatus.ACTIVE && now.isBefore(t.getExpiresAt()))
                .map(RefreshToken::getSession)
                .filter(s -> s.getStatus() == AuthSessionStatus.ACTIVE && now.isBefore(s.getAbsoluteExpiresAt())
                        && now.isBefore(s.getIdleExpiresAt()))
                .flatMap(s -> resolvePrincipal(s).map(p -> new Peeked(s, p)));
    }

    // ------------------------------------------------------------------------------------------------ cleanup

    /** Marks overdue sessions (the checks above never depend on this) and purges old rows. */
    @Transactional
    public void cleanup() {
        Instant now = clock.instant();
        int absolute = sessions.expireAbsolute(now, SessionEndReason.ABSOLUTE);
        int idle = sessions.expireIdle(now, SessionEndReason.IDLE);
        int deletedSessions = sessions.deleteEndedBefore(now.minus(Duration.ofDays(90)));
        int deletedTokens = tokens.deleteExpiredBefore(now.minus(Duration.ofDays(30)));
        if (absolute + idle + deletedSessions + deletedTokens > 0) {
            log.info("Auth cleanup: {} absolute, {} idle expired, {} sessions and {} refresh tokens purged",
                    absolute, idle, deletedSessions, deletedTokens);
        }
        guardCache.clear();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Optional<Principal> resolvePrincipal(AuthSession session) {
        if (session.isAdminSession()) {
            return admins.findById(session.getAdminUserId()).filter(AdminUser::isActive)
                    .map(a -> new Principal(PrincipalType.ADMIN, a.getId(), a.getRole().name()));
        }
        return users.findById(session.getUserId()).filter(User::isActive)
                .map(u -> new Principal(PrincipalType.USER, u.getId(), "USER"));
    }

    private IssuedTokens issue(AuthSession session, Principal principal, String rawRefresh, Instant refreshExpires,
                               AuthPolicy policy, Instant now) {
        Instant accessExpires = earlier(now.plus(policy.accessTtl()), session.getAbsoluteExpiresAt());
        String access = jwtService.generateAccessToken(principal.id().toString(), principal.role(), principal.type().name(),
                session.getId(), now, accessExpires);
        return new IssuedTokens(access, accessExpires, rawRefresh, refreshExpires, session, principal);
    }

    private static RefreshToken newToken(AuthSession session, int generation, String raw, Instant now, Instant expires,
                                         InetAddress ip) {
        return RefreshToken.builder()
                .session(session)
                .tokenHash(TokenHasher.sha256(raw))
                .generation(generation)
                .status(RefreshTokenStatus.ACTIVE)
                .issuedAt(now)
                .expiresAt(expires)
                .issuedIp(ip)
                .build();
    }

    private static RefreshOutcome failure(ErrorCode code) {
        return new RefreshOutcome(null, code);
    }

    private static EventActorType actor(Principal principal) {
        return principal.type() == PrincipalType.ADMIN ? EventActorType.ADMIN : EventActorType.USER;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
