package com.nimokids.security;

import com.nimokids.service.auth.SessionGuard;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.config.SecurityConfig;
import com.nimokids.controller.TopicController;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.TopicService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/admin/** needs a valid JWT (401 otherwise); each admin API then states the role it needs with @PreAuthorize
 * (403 when the role is not enough). Every other API stays public.
 */
@WebMvcTest(controllers = {TopicController.class, AdminSecurityTest.AdminProbeController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class, AdminSecurityTest.AdminProbeController.class})
class AdminSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private TopicService topicService;
    @MockitoBean private SessionGuard sessionGuard;
    @MockitoBean private ApiLogService apiLogService;

    @Test
    void adminEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void theRoleFromTheTokenDecidesWhichAdminApisAreAllowed() throws Exception {
        String superAdmin = "Bearer " + jwtService.generateToken("boss", "SUPER_ADMIN");
        String admin = "Bearer " + jwtService.generateToken("staff", "ADMIN");
        String unknownRole = "Bearer " + jwtService.generateToken("someone", "EDITOR");

        // /ping needs SUPER_ADMIN, /any only needs a logged-in admin
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", superAdmin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "bearer " + jwtService.generateToken("boss", "SUPER_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", unknownRole)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/any").header("Authorization", superAdmin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/any").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/any")).andExpect(status().isUnauthorized());
    }

    @Test
    void theRoleInTheTokenBecomesTheAuthorityRolePrefixedAndIsReadableInTheController() throws Exception {
        mockMvc.perform(get("/api/v1/admin/who").header("Authorization", "Bearer " + jwtService.generateToken("staff-7", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("staff-7|ADMIN|ROLE_ADMIN"));
    }

    @Test
    void invalidTokensAreRejectedWithoutRevealingWhy() throws Exception {
        String valid = jwtService.generateToken("admin-1", "ADMIN");
        String expired = jwtService.generateToken("admin-1", "ADMIN", Duration.ofMinutes(-5));
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        String wrongKey = Jwts.builder().issuer(JwtService.ISSUER).subject("admin-1").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-32-byte-key!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String unsigned = Jwts.builder().issuer(JwtService.ISSUER).subject("admin-1").claim("role", "ADMIN").compact();
        String wrongIssuer = Jwts.builder().issuer("someone-else").subject("admin-1").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-32-byte-key!!".getBytes(StandardCharsets.UTF_8)))
                .compact();

        // The client needs one distinction only: an EXPIRED token is worth a refresh, anything else is a sign-in.
        // Why a token is invalid (bad signature, wrong issuer ...) is still never revealed.
        for (String token : List.of("garbage", tampered, wrongKey, unsigned, wrongIssuer, "")) {
            mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        }
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminProtectionCoversTheBasePathAndTrailingSlash() throws Exception {
        mockMvc.perform(get("/api/v1/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/ping/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/anything/deeper")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyListedPublicAreaIsReachableWithoutAnyToken() throws Exception {
        // No controller exists for some of these areas yet, so "not 401/403" (here: 404 from MVC) proves security let it through.
        for (String path : List.of("/api/v1/game/sessions", "/api/v1/game-sessions", "/api/v1/topics", "/api/v1/game-modes",
                "/api/v1/media/1", "/api/v1/activities/batch", "/api/v1/players/me/stickers")) {
            int httpStatus = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(httpStatus).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void anythingNotOnThePublicListOrTheAdminAreaIsDenied() throws Exception {
        for (String path : List.of("/api/v1/unknown", "/api/v1/users", "/api/v1/auth", "/api/v1/auth/register", "/api", "/", "/actuator/health")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void loginIsOnlyOpenForPost() throws Exception {
        int get = mockMvc.perform(get("/api/v1/auth/login")).andReturn().getResponse().getStatus();
        int post = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(get).isEqualTo(401);
        // POST passes security (the controller is not part of this slice, so MVC answers 404/405, not 401/403).
        org.assertj.core.api.Assertions.assertThat(post).isNotIn(401, 403);
    }

    @Test
    void anAdminTokenSentToAPlayerEndpointChangesNothing() throws Exception {
        String token = jwtService.generateToken("admin-1", "ADMIN");

        mockMvc.perform(get("/api/v1/topics").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/unknown").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicApiIgnoresABadTokenInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/api/v1/topics").header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk());
    }

    @RestController
    public static class AdminProbeController {

        @GetMapping("/api/v1/admin/ping")
        @PreAuthorize("hasRole('SUPER_ADMIN')")
        public String ping() {
            return "pong";
        }

        @GetMapping("/api/v1/admin/any")
        @PreAuthorize("isAuthenticated()")
        public String any() {
            return "ok";
        }

        @GetMapping("/api/v1/admin/who")
        @PreAuthorize("isAuthenticated()")
        public String who(@AuthenticationPrincipal AdminPrincipal admin, Authentication authentication) {
            return admin.userId() + "|" + admin.role() + "|" + authentication.getAuthorities().iterator().next();
        }
    }
}
