package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.config.SecurityConfig;
import com.nimokids.controller.AuthController;
import com.nimokids.entity.AdminUser;
import com.nimokids.entity.enums.AdminRole;
import com.nimokids.repository.AdminUserRepository;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.impl.AuthServiceImpl;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real login service + real JWT + real security rules; only the users table is mocked. */
@WebMvcTest(controllers = {AuthController.class, LoginFlowTest.AdminProbeController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class,
        AuthServiceImpl.class, LoginFlowTest.AdminProbeController.class})
class LoginFlowTest {

    private static final String ADMIN_EMAIL = "admin@nimokids.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AdminUserRepository userRepository;
    @MockitoBean private ApiLogService apiLogService;

    private AdminUser admin;

    @BeforeEach
    void setUp() {
        admin = user(ADMIN_EMAIL, "admin123", true);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
    }

    @Test
    void adminLoginReturnsAJwtWithTheRoleClaimThatOpensAdminEndpoints() throws Exception {
        String token = login(ADMIN_EMAIL, "admin123");

        assertThat(jwtService.parse(token)).contains(new JwtPrincipal(admin.getId().toString(), "SUPER_ADMIN"));
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginResponseDescribesTheTokenAndNeverLeaksThePasswordHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("$2a$"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("password"))));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        when(userRepository.findByEmailIgnoreCase("ADMIN@NimoKids.LOCAL")).thenReturn(Optional.of(admin));

        assertThat(login("ADMIN@NimoKids.LOCAL", "admin123")).isNotBlank();
    }

    @Test
    void wrongPasswordUnknownEmailAndInactiveAccountAllLookIdentical() throws Exception {
        AdminUser inactive = user("gone@nimokids.local", "secret1", false);
        when(userRepository.findByEmailIgnoreCase("gone@nimokids.local")).thenReturn(Optional.of(inactive));

        String[][] attempts = {{ADMIN_EMAIL, "wrong-password"}, {"nobody@nimokids.local", "admin123"}, {"gone@nimokids.local", "secret1"}};
        for (String[] attempt : attempts) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", attempt[0], "password", attempt[1]))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void loginRequestIsValidated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + "p".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='password')]").exists());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOldUsernameFieldIsNoLongerAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='email')]").exists());
    }

    @Test
    void onlyPostIsAllowedOnTheLoginUrl() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsStillRejectRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private AdminUser user(String email, String rawPassword, boolean active) {
        AdminUser created = AdminUser.builder()
                .email(email).passwordHash(passwordEncoder.encode(rawPassword)).role(AdminRole.SUPER_ADMIN).active(active).build();
        created.setId(UUID.randomUUID());
        return created;
    }

    @RestController
    static class AdminProbeController {

        @GetMapping("/api/v1/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
