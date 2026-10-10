package com.nimokids.service.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimokids.config.TimeConfig;
import com.nimokids.entity.User;
import com.nimokids.entity.enums.AuthSessionStatus;
import com.nimokids.entity.enums.UserStatus;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AuthSessionRepository;
import com.nimokids.repository.OAuthAccountRepository;
import com.nimokids.repository.UserRepository;
import com.nimokids.security.JwtProperties;
import com.nimokids.security.JwtService;
import com.nimokids.service.auth.AuthPolicyService;
import com.nimokids.service.auth.AuthProperties;
import com.nimokids.service.auth.AuthSessionService;
import com.nimokids.service.auth.ClientContext;
import com.nimokids.service.auth.ClientIpResolver;
import com.nimokids.service.auth.LoginHistoryService;
import com.nimokids.service.auth.SecurityEventService;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The Google login against a REAL PostgreSQL and a LOCAL stub of Google (token endpoint + JWKS), so the whole path is
 * exercised without any network or credentials: PKCE, state, nonce, signature, audience, expiry, account resolution.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GoogleLoginService.class, GoogleTokenClient.class, GoogleIdTokenVerifier.class, GoogleJwks.class, SecretBox.class,
        AuthSessionService.class, AuthPolicyService.class, LoginHistoryService.class, SecurityEventService.class,
        ClientIpResolver.class, JwtService.class, TimeConfig.class, JacksonAutoConfiguration.class,
        GoogleLoginServiceIntegrationTest.TestBeans.class})
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class GoogleLoginServiceIntegrationTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String KEY_ID = "test-key-1";
    private static final KeyPair GOOGLE_KEY = rsa();
    private static final KeyPair OTHER_KEY = rsa();
    private static volatile String nextIdToken = "";
    private static volatile int tokenEndpointStatus = 200;
    private static HttpServer server;

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        GoogleOidcProperties googleOidcProperties() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            RSAPublicKey pub = (RSAPublicKey) GOOGLE_KEY.getPublic();
            String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KEY_ID + "\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
                    + b64(pub.getModulus().toByteArray()) + "\",\"e\":\"" + b64(pub.getPublicExponent().toByteArray()) + "\"}]}";
            server.createContext("/certs", exchange -> reply(exchange, 200, jwks));
            server.createContext("/token", exchange -> reply(exchange, tokenEndpointStatus, "{\"id_token\":\"" + nextIdToken + "\"}"));
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            return new GoogleOidcProperties(CLIENT_ID, "test-secret", "https://nimokids.test/api/v1/auth/google/callback",
                    base + "/authorize", base + "/token", base + "/certs", 2);
        }

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(false, List.of(), "https://nimokids.test");
        }

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("bmltb2tpZHMtZGV2LW9ubHktand0LXNlY3JldC1rZXktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTAxMjM0NTY3ODk=", 60);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String b64(byte[] bytes) {
        int start = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;   // BigInteger adds a sign byte
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, start, bytes.length));
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Autowired private GoogleLoginService service;
    @Autowired private UserRepository users;
    @Autowired private OAuthAccountRepository oauthAccounts;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private EntityManager em;

    private ClientContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        ctx = new ClientContext(InetAddress.getByName("113.22.10.4"), "UA", "Chrome", "Windows", "DESKTOP", new byte[]{9, 9}, null);
        tokenEndpointStatus = 200;
    }

    // ----------------------------------------------------------------------------------------------- helpers

    private record Started(String state, String nonce, String url) {
    }

    private Started start(String returnTo) {
        String url = service.start(returnTo, ctx);
        Map<String, String> query = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return new Started(query.get("state"), query.get("nonce"), url);
    }

    private String token(String subject, String email, boolean verified, String nonce, String audience, Instant expires, KeyPair signer) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("https://accounts.google.com").subject(subject).audience().add(audience).and()
                .issuedAt(Date.from(now.minusSeconds(5))).expiration(Date.from(expires))
                .claim("email", email).claim("email_verified", verified).claim("name", "Parent " + subject)
                .claim("picture", "https://lh3.example/p.jpg").claim("nonce", nonce)
                .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String goodToken(String subject, String email, String nonce) {
        return token(subject, email, true, nonce, CLIENT_ID, Instant.now().plus(Duration.ofMinutes(5)), GOOGLE_KEY);
    }

    private GoogleLoginService.CallbackResult login(String subject, String email) {
        Started started = start("/topics");
        nextIdToken = goodToken(subject, email, started.nonce());
        return service.callback("auth-code", started.state(), null, ctx);
    }

    private static String errorCodeOf(GoogleLoginService.CallbackResult result) {
        assertThat(result.tokens()).as("no session may exist").isNull();
        assertThat(result.redirectUrl()).startsWith("https://nimokids.test/auth/error?code=");
        return result.redirectUrl().substring(result.redirectUrl().indexOf("code=") + 5);
    }

    // ----------------------------------------------------------------------------------------------- start

    @Test
    void theAuthorizationAddressCarriesPkceStateAndNonce() {
        Started started = start("/age");

        assertThat(started.url()).contains("response_type=code", "client_id=test-client-id", "code_challenge_method=S256",
                "scope=openid+email+profile", "prompt=select_account", "redirect_uri=https%3A%2F%2Fnimokids.test");
        assertThat(started.state()).hasSizeGreaterThan(30);
        assertThat(started.nonce()).hasSizeGreaterThan(30).isNotEqualTo(started.state());
        assertThat(started.url()).doesNotContain("test-secret");
    }

    @Test
    void aReturnAddressOutsideTheSiteIsRefusedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.start("//evil.com", ctx))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RETURN_TO_INVALID));
        assertThatThrownBy(() -> service.start("https://evil.com", ctx)).isInstanceOf(BusinessException.class);
    }

    // ----------------------------------------------------------------------------------------------- success paths

    @Test
    void aFirstSignInCreatesTheAccountAndASessionAndReturnsToThePageTheUserCameFrom() {
        String subject = "g-" + UUID.randomUUID();

        GoogleLoginService.CallbackResult result = login(subject, "parent@example.com");

        assertThat(result.redirectUrl()).isEqualTo("https://nimokids.test/auth/complete?returnTo=%2Ftopics");
        assertThat(result.tokens()).isNotNull();
        assertThat(result.tokens().principal().type().name()).isEqualTo("USER");
        assertThat(result.tokens().refreshToken()).hasSize(43);
        assertThat(result.tokens().session().getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(oauthAccounts.findByProviderAndProviderSubject("GOOGLE", subject)).isPresent();
        User user = users.findById(result.tokens().principal().id()).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("parent@example.com");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void aReturningUserIsFoundByTheGoogleSubjectEvenWhenTheEmailChanged() {
        String subject = "g-" + UUID.randomUUID();
        UUID first = login(subject, "old@example.com").tokens().principal().id();

        GoogleLoginService.CallbackResult second = login(subject, "new@example.com");

        assertThat(second.tokens().principal().id()).isEqualTo(first);
        assertThat(users.findById(first).orElseThrow().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void theSameEmailWithAnotherGoogleSubjectIsNeverMergedIntoTheExistingAccount() {
        String email = "shared-" + UUID.randomUUID() + "@example.com";
        UUID first = login("g-" + UUID.randomUUID(), email).tokens().principal().id();

        UUID second = login("g-" + UUID.randomUUID(), email).tokens().principal().id();

        assertThat(second).isNotEqualTo(first);
    }

    // ----------------------------------------------------------------------------------------------- refusals

    @Test
    void anUnverifiedEmailIsRefused() {
        Started started = start("/");
        nextIdToken = token("g-1", "x@example.com", false, started.nonce(), CLIENT_ID, Instant.now().plusSeconds(300), GOOGLE_KEY);

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void aWrongNonceIsRefused() {
        Started started = start("/");
        nextIdToken = goodToken("g-2", "x@example.com", "some-other-nonce");

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("ID_TOKEN_INVALID");
    }

    @Test
    void aTokenForAnotherClientIsRefused() {
        Started started = start("/");
        nextIdToken = token("g-3", "x@example.com", true, started.nonce(), "someone-elses-client", Instant.now().plusSeconds(300), GOOGLE_KEY);

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("ID_TOKEN_INVALID");
    }

    @Test
    void anExpiredTokenIsRefused() {
        Started started = start("/");
        nextIdToken = token("g-4", "x@example.com", true, started.nonce(), CLIENT_ID, Instant.now().minusSeconds(600), GOOGLE_KEY);

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("ID_TOKEN_INVALID");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() {
        Started started = start("/");
        nextIdToken = token("g-5", "x@example.com", true, started.nonce(), CLIENT_ID, Instant.now().plusSeconds(300), OTHER_KEY);

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("ID_TOKEN_INVALID");
    }

    @Test
    void aTokenUsingAnotherAlgorithmIsRefused() {
        Started started = start("/");
        // HS256 signed with the PUBLIC key bytes as the secret: the classic algorithm-confusion attack
        byte[] publicKeyBytes = GOOGLE_KEY.getPublic().getEncoded();
        nextIdToken = Jwts.builder().header().keyId(KEY_ID).and().issuer("https://accounts.google.com").subject("g-6")
                .audience().add(CLIENT_ID).and().issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("email", "x@example.com").claim("email_verified", true).claim("nonce", started.nonce())
                .signWith(new SecretKeySpec(publicKeyBytes, "HmacSHA256")).compact();

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("ID_TOKEN_INVALID");
    }

    @Test
    void anUnknownStateIsRefusedAndAUsedStateCannotBeReplayed() {
        assertThat(errorCodeOf(service.callback("c", "never-issued", null, ctx))).isEqualTo("OAUTH_STATE_INVALID");
        assertThat(errorCodeOf(service.callback("c", null, null, ctx))).isEqualTo("OAUTH_STATE_INVALID");

        Started started = start("/");
        nextIdToken = goodToken("g-7-" + UUID.randomUUID(), "x@example.com", started.nonce());
        assertThat(service.callback("c", started.state(), null, ctx).tokens()).isNotNull();
        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).as("replay").isEqualTo("OAUTH_STATE_INVALID");
    }

    @Test
    void cancellingAtGoogleIsAFriendlyOutcomeNotAnError() {
        Started started = start("/");

        assertThat(errorCodeOf(service.callback(null, started.state(), "access_denied", ctx))).isEqualTo("OAUTH_CANCELLED");
    }

    @Test
    void aGoogleOutageIsAProviderErrorAndStartsNoSession() {
        Started started = start("/");
        tokenEndpointStatus = 500;

        assertThat(errorCodeOf(service.callback("c", started.state(), null, ctx))).isEqualTo("OAUTH_PROVIDER_ERROR");
    }

    @Test
    void aDisabledAccountCannotSignIn() {
        String subject = "g-" + UUID.randomUUID();
        UUID id = login(subject, "blocked@example.com").tokens().principal().id();
        User user = users.findById(id).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        long before = sessions.count();

        GoogleLoginService.CallbackResult again = login(subject, "blocked@example.com");

        assertThat(errorCodeOf(again)).isEqualTo("ACCOUNT_DISABLED");
        assertThat(sessions.count()).isEqualTo(before);
    }

    @Test
    void failuresAreWrittenToTheLoginHistoryWithASafeCodeAndNoToken() {
        Started started = start("/");
        nextIdToken = goodToken("g-8", "x@example.com", "wrong");
        service.callback("secret-authorization-code", started.state(), null, ctx);
        em.flush();

        Number rows = (Number) em.createNativeQuery("select count(*) from login_history where method = 'GOOGLE' and outcome = 'FAILED' and failure_code = 'ID_TOKEN_INVALID'").getSingleResult();
        Number leaks = (Number) em.createNativeQuery("select count(*) from login_history h where h::text like '%secret-authorization-code%' or h::text like '%eyJ%'").getSingleResult();
        assertThat(rows.intValue()).isGreaterThanOrEqualTo(1);
        assertThat(leaks.intValue()).isZero();
    }
}
