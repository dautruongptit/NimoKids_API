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
import com.nimokids.config.TimeConfig;
import com.nimokids.controller.AuthController;
import com.nimokids.entity.AppUser;
import com.nimokids.entity.enums.UserRole;
import com.nimokids.repository.AppUserRepository;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.impl.AuthServiceImpl;
import java.util.Optional;
import java.util.UUID;
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

/** Real login service + real JWT + real security rules; only the user table is mocked. */
@WebMvcTest(controllers = {AuthController.class, LoginFlowTest.AdminProbeController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, TimeConfig.class,
        AuthServiceImpl.class, LoginFlowTest.AdminProbeController.class})
class LoginFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AppUserRepository userRepository;
    @MockitoBean private ApiLogService apiLogService;

    private AppUser admin;
    private AppUser regular;

    @BeforeEach
    void setUp() {
        admin = user("admin", "admin123", UserRole.ADMIN, true);
        regular = user("user", "user123", UserRole.USER, true);
        when(userRepository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(regular));
    }

    @Test
    void adminLoginReturnsAJwtWithTheRoleClaimThatOpensAdminEndpoints() throws Exception {
        String token = login("admin", "admin123");

        assertThat(jwtService.parse(token)).contains(new JwtPrincipal(admin.getId().toString(), "ADMIN"));
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(admin.getLastLoginAt()).isNotNull();
    }

    @Test
    void loginResponseDescribesTheTokenAndNeverLeaksTheHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2a$"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        when(userRepository.findByUsernameIgnoreCase("ADMIN")).thenReturn(Optional.of(admin));

        assertThat(login("ADMIN", "admin123")).isNotBlank();
    }

    @Test
    void regularUserCanLogInButIsForbiddenFromAdminEndpoints() throws Exception {
        String token = login("user", "user123");

        assertThat(jwtService.parse(token)).contains(new JwtPrincipal(regular.getId().toString(), "USER"));
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveUserAllLookIdentical() throws Exception {
        AppUser inactive = user("gone", "secret1", UserRole.ADMIN, false);
        when(userRepository.findByUsernameIgnoreCase("gone")).thenReturn(Optional.of(inactive));

        String[][] attempts = {{"admin", "wrong-password"}, {"nobody", "admin123"}, {"gone", "secret1"}};
        for (String[] attempt : attempts) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + attempt[0] + "\",\"password\":\"" + attempt[1] + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        assertThat(admin.getLastLoginAt()).isNull();
    }

    @Test
    void loginRequestIsValidated() throws Exception {
        String tooLong = "p".repeat(73);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='username')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='password')]").exists());
    }

    @Test
    void adminEndpointsStillRejectRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private AppUser user(String username, String rawPassword, UserRole role, boolean active) {
        AppUser appUser = AppUser.builder()
                .username(username).passwordHash(passwordEncoder.encode(rawPassword)).role(role).active(active).build();
        appUser.setId(UUID.randomUUID());
        return appUser;
    }

    @RestController
    static class AdminProbeController {

        @GetMapping("/api/v1/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
