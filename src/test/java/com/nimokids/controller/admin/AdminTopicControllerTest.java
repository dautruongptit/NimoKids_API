package com.nimokids.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.config.SecurityConfig;
import com.nimokids.security.JwtService;
import com.nimokids.security.SecurityErrorHandler;
import com.nimokids.security.StaticRoleAuthorityResolver;
import com.nimokids.service.ApiLogService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The sample for every future admin API: JWT -> role in the payload -> ROLE_ authority -> @PreAuthorize. */
@WebMvcTest(controllers = AdminTopicController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class})
class AdminTopicControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private ApiLogService apiLogService;

    @Test
    void superAdminCanCallTheEndpointAndTheControllerSeesWhoIsCalling() throws Exception {
        String token = jwtService.generateToken("11111111-1111-4111-8111-111111111111", "SUPER_ADMIN");

        mockMvc.perform(get("/api/v1/admin/topics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.callerId").value("11111111-1111-4111-8111-111111111111"))
                .andExpect(jsonPath("$.data.callerRole").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.topics").isArray());
    }

    @Test
    void anAdminWithoutTheSuperAdminRoleGets403InTheStandardEnvelope() throws Exception {
        String token = jwtService.generateToken("staff", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/topics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void noTokenOrABadTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/topics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin/topics").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/topics").header("Authorization",
                        "Bearer " + jwtService.generateToken("boss", "SUPER_ADMIN", Duration.ofMinutes(-1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void methodsThatAreNotMappedAreNotReachableEvenWithTheRightRole() throws Exception {
        String token = jwtService.generateToken("boss", "SUPER_ADMIN");

        mockMvc.perform(post("/api/v1/admin/topics").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }
}
