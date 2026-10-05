package com.nimokids.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/admin/** needs a valid JWT with role ADMIN; every other API stays public. */
@WebMvcTest(controllers = {TopicController.class, AdminSecurityTest.AdminProbeController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, AdminSecurityTest.AdminProbeController.class})
class AdminSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private TopicService topicService;
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
    void adminEndpointAcceptsAValidAdminToken() throws Exception {
        String token = jwtService.generateToken("admin-1", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void validTokenWithoutAdminRoleIsForbidden() throws Exception {
        String token = jwtService.generateToken("someone", "USER");

        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
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

        for (String token : List.of("garbage", expired, tampered, wrongKey, unsigned, wrongIssuer, "")) {
            mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
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
    void publicApiIgnoresABadTokenInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/api/v1/topics").header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk());
    }

    @RestController
    static class AdminProbeController {

        @GetMapping("/api/v1/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
