package com.nimokids.service.auth.google;

import com.nimokids.entity.OAuthAccount;
import com.nimokids.entity.OAuthLoginAttempt;
import com.nimokids.entity.User;
import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.EventSeverity;
import com.nimokids.entity.enums.LoginOutcome;
import com.nimokids.entity.enums.PrincipalType;
import com.nimokids.entity.enums.UserStatus;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.OAuthAccountRepository;
import com.nimokids.repository.OAuthLoginAttemptRepository;
import com.nimokids.repository.UserRepository;
import com.nimokids.service.auth.AuthPolicyService;
import com.nimokids.service.auth.AuthProperties;
import com.nimokids.service.auth.AuthSessionService;
import com.nimokids.service.auth.AuthSessionService.IssuedTokens;
import com.nimokids.service.auth.AuthSessionService.Principal;
import com.nimokids.service.auth.ClientContext;
import com.nimokids.service.auth.IpMasker;
import com.nimokids.service.auth.LoginHistoryService;
import com.nimokids.service.auth.SecurityEventService;
import com.nimokids.service.auth.TokenHasher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in with Google: Authorization Code + PKCE (S256), run by the backend as a confidential client.
 *
 * The user is identified by the Google subject ("sub"), never by the e-mail. Two accounts are never merged because the
 * e-mail looks the same. The ID token is verified and then discarded; the application's own session takes over.
 * Failures never explain themselves to the browser: it is sent to the site with a short code, the details stay in
 * login_history / security_events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    /** Where the browser goes next. {@code tokens} is set only on success (the controller sets the cookie). */
    public record CallbackResult(String redirectUrl, IssuedTokens tokens) {
    }

    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);
    private static final String PROVIDER = "GOOGLE";

    private final GoogleOidcProperties properties;
    private final AuthProperties authProperties;
    private final OAuthLoginAttemptRepository attempts;
    private final OAuthAccountRepository oauthAccounts;
    private final UserRepository users;
    private final GoogleTokenClient tokenClient;
    private final GoogleIdTokenVerifier verifier;
    private final SecretBox secretBox;
    private final AuthSessionService sessionService;
    private final AuthPolicyService policyService;
    private final LoginHistoryService loginHistory;
    private final SecurityEventService events;
    private final Clock clock;

    public boolean enabled() {
        return properties.enabled();
    }

    // ------------------------------------------------------------------------------------------------ step 1

    /** Prepares the login and returns the Google address the browser must be sent to. */
    @Transactional
    public String start(String returnTo, ClientContext ctx) {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.OAUTH_UNAVAILABLE);
        }
        if (!ReturnToValidator.isValid(returnTo)) {
            events.security("RETURN_TO_REJECTED", EventSeverity.WARN, null, null, null, ctx, Map.of());
            throw new BusinessException(ErrorCode.RETURN_TO_INVALID);
        }
        Instant now = clock.instant();
        String state = Pkce.randomToken(32);
        String nonce = Pkce.randomToken(32);
        String verifierValue = Pkce.newVerifier();
        attempts.save(OAuthLoginAttempt.builder()
                .stateHash(TokenHasher.sha256(state))
                .nonceHash(TokenHasher.sha256(nonce))
                .codeVerifierEnc(secretBox.seal(verifierValue))
                .returnTo(ReturnToValidator.orDefault(returnTo))
                .ip(ctx.ip())
                .userAgent(ctx.userAgent())
                .createdAt(now)
                .expiresAt(now.plus(ATTEMPT_TTL))
                .build());
        return properties.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + enc(properties.clientId())
                + "&redirect_uri=" + enc(properties.redirectUri())
                + "&scope=" + enc("openid email profile")
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&code_challenge=" + enc(Pkce.challenge(verifierValue))
                + "&code_challenge_method=S256"
                + "&prompt=select_account";
    }

    // ------------------------------------------------------------------------------------------------ step 2

    /**
     * Handles Google's redirect. Never throws for a login failure: it returns the redirect to the site's error page.
     * (noRollbackFor: the attempt is marked as used even when the login then fails.)
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public CallbackResult callback(String code, String state, String error, ClientContext ctx) {
        if (!properties.enabled()) {
            return failure("OAUTH_UNAVAILABLE", null, ctx, null, false);
        }
        Instant now = clock.instant();
        OAuthLoginAttempt attempt = state == null || state.isBlank() ? null
                : attempts.findByStateHash(TokenHasher.sha256(state)).orElse(null);
        if (attempt == null || attempts.consume(attempt.getId(), now) != 1) {
            // unknown, already used or expired: the classic sign of a replayed or forged callback
            return failure("OAUTH_STATE_INVALID", null, ctx, null, true);
        }
        if (error != null && !error.isBlank()) {
            return failure("access_denied".equals(error) ? "OAUTH_CANCELLED" : "OAUTH_PROVIDER_ERROR", null, ctx, null, false);
        }
        if (code == null || code.isBlank()) {
            return failure("OAUTH_PROVIDER_ERROR", null, ctx, null, false);
        }

        String idToken;
        try {
            idToken = tokenClient.exchange(code, secretBox.open(attempt.getCodeVerifierEnc()));
        } catch (GoogleTokenClient.ExchangeFailedException ex) {
            log.warn("Google code exchange failed: {}", ex.getMessage());
            return failure("OAUTH_PROVIDER_ERROR", null, ctx, null, false);
        }

        Optional<GoogleIdTokenVerifier.Identity> verified = verifier.verify(idToken);
        if (verified.isEmpty()) {
            return failure("ID_TOKEN_INVALID", null, ctx, null, true);
        }
        GoogleIdTokenVerifier.Identity identity = verified.get();
        if (identity.nonce() == null || !MessageDigest.isEqual(TokenHasher.sha256(identity.nonce()), attempt.getNonceHash())) {
            return failure("ID_TOKEN_INVALID", null, ctx, null, true);
        }
        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            return failure("EMAIL_NOT_VERIFIED", null, ctx, IpMasker.maskEmail(identity.email()), false);
        }

        User user = resolveUser(identity, ctx, now);
        if (user.getStatus() != UserStatus.ACTIVE) {
            LoginOutcome outcome = user.getStatus() == UserStatus.DISABLED ? LoginOutcome.BLOCKED : LoginOutcome.FAILED;
            loginHistory.record(user.getId(), null, null, AuthMethod.GOOGLE, outcome, "ACCOUNT_DISABLED", ctx,
                    IpMasker.maskEmail(identity.email()));
            events.security("ACCOUNT_DISABLED", EventSeverity.WARN, null, user.getId(), null, ctx, Map.of("where", "google-login"));
            return new CallbackResult(errorUrl("ACCOUNT_DISABLED"), null);
        }

        user.setLastLoginAt(now);
        users.save(user);
        IssuedTokens issued = sessionService.createSession(new Principal(PrincipalType.USER, user.getId(), "USER"), AuthMethod.GOOGLE, ctx);
        LoginHistoryService.Recorded recorded = loginHistory.record(user.getId(), null, issued.session().getId(),
                AuthMethod.GOOGLE, LoginOutcome.SUCCESS, null, ctx, null);
        issued.session().setLoginHistoryId(recorded.id());
        if (policyService.current().detectNewDevice() && recorded.newDevice()) {
            events.security("NEW_DEVICE_LOGIN", EventSeverity.INFO, issued.session().getId(), user.getId(), null, ctx,
                    Map.of("newIp", recorded.newIp()));
        }
        String completeUrl = authProperties.webUrl().replaceAll("/+$", "") + "/auth/complete?returnTo=" + enc(attempt.getReturnTo());
        return new CallbackResult(completeUrl, issued);
    }

    // ------------------------------------------------------------------------------------------------ accounts

    /**
     * The account of this Google identity, created on the first sign-in. The key is the Google subject. A different
     * account with the same e-mail is NOT linked: it is only noted, so a future second provider cannot cause a takeover.
     */
    private User resolveUser(GoogleIdTokenVerifier.Identity identity, ClientContext ctx, Instant now) {
        Optional<OAuthAccount> linked = oauthAccounts.findByProviderAndProviderSubject(PROVIDER, identity.subject());
        if (linked.isPresent()) {
            OAuthAccount account = linked.get();
            User user = account.getUser();
            if (user.getStatus() == UserStatus.ACTIVE) {
                // the e-mail, name and picture are only a snapshot of what Google says now
                user.setEmail(identity.email());
                user.setEmailVerified(true);
                user.setDisplayName(limit(identity.name(), 100));
                user.setAvatarUrl(limit(identity.picture(), 500));
                user.setLocale(limit(identity.locale(), 10));
            }
            account.setLastLoginAt(now);
            oauthAccounts.save(account);
            return user;
        }
        List<User> sameEmail = users.findAllByEmailIgnoreCase(identity.email());
        User created = users.save(User.builder()
                .email(identity.email())
                .emailVerified(true)
                .displayName(limit(identity.name(), 100))
                .avatarUrl(limit(identity.picture(), 500))
                .locale(limit(identity.locale(), 10))
                .status(UserStatus.ACTIVE)
                .build());
        oauthAccounts.save(OAuthAccount.builder()
                .user(created)
                .provider(PROVIDER)
                .providerSubject(identity.subject())
                .emailAtLink(identity.email())
                .emailVerifiedAtLink(true)
                .lastLoginAt(now)
                .build());
        if (!sameEmail.isEmpty()) {
            events.security("ACCOUNT_LINK_CONFLICT", EventSeverity.WARN, null, created.getId(), null, ctx,
                    Map.of("existingAccounts", sameEmail.size()));
        }
        return created;
    }

    // ------------------------------------------------------------------------------------------------ failures

    private CallbackResult failure(String code, java.util.UUID userId, ClientContext ctx, String hint, boolean suspicious) {
        loginHistory.record(userId, null, null, AuthMethod.GOOGLE,
                "OAUTH_CANCELLED".equals(code) ? LoginOutcome.FAILED : LoginOutcome.FAILED, code, ctx, hint);
        if (suspicious) {
            events.security(code, EventSeverity.WARN, null, userId, null, ctx, Map.of());
        }
        return new CallbackResult(errorUrl(code), null);
    }

    private String errorUrl(String code) {
        return authProperties.webUrl().replaceAll("/+$", "") + "/auth/error?code=" + enc(code);
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
