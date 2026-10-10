package com.nimokids.logging;

import com.nimokids.service.auth.SessionGuard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.config.SecurityConfig;
import com.nimokids.controller.GameSessionController;
import com.nimokids.controller.TopicController;
import com.nimokids.exception.SessionNotFoundException;
import com.nimokids.security.JwtService;
import com.nimokids.security.SecurityErrorHandler;
import com.nimokids.security.StaticRoleAuthorityResolver;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.TopicService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The filter only gathers data and hands it to the async ApiLogService; it must never affect the response. */
@WebMvcTest(controllers = {TopicController.class, GameSessionController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class})
class ApiLoggingFilterTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ApiLogService apiLogService;
    @MockitoBean private SessionGuard sessionGuard;
    @MockitoBean private TopicService topicService;
    @MockitoBean private GameSessionService gameSessionService;

    @BeforeEach
    void resetMocks() {
        reset(apiLogService);
    }

    @Test
    void successfulRequestIsLoggedWithRouteStatusTimeAndRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(topicService.getPlayableTopics(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-Request-Id", requestId.toString())
                        .header("User-Agent", "JUnit")
                        .header("Authorization", "Bearer super-secret-token")
                        .header("Cookie", "sid=secret"))
                .andExpect(status().isOk());

        ApiLogEvent event = capture();
        assertThat(event.requestId()).isEqualTo(requestId);
        assertThat(event.httpMethod()).isEqualTo("GET");
        assertThat(event.endpoint()).isEqualTo("/api/v1/topics");
        assertThat(event.statusCode()).isEqualTo(200);
        assertThat(event.responseTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.userAgent()).isEqualTo("JUnit");
        assertThat(event.responseSizeBytes()).isPositive();
        assertThat(event.errorCode()).isNull();
        // The event has no field for headers, so credentials cannot be logged.
        assertThat(event.toString()).doesNotContain("super-secret-token").doesNotContain("sid=secret");
    }

    @Test
    void businessErrorIsLoggedWithTheStableErrorCodeAndTheSessionRoute() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(gameSessionService.getSession(any(), any())).thenThrow(new SessionNotFoundException());

        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId + "?secret=1")
                        .header("X-Anonymous-Id", anonymousId.toString()))
                .andExpect(status().isNotFound());

        ApiLogEvent event = capture();
        assertThat(event.endpoint()).isEqualTo("/api/v1/game-sessions/{sessionId}");
        assertThat(event.statusCode()).isEqualTo(404);
        assertThat(event.errorCode()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(event.errorMessage()).isEqualTo("Game session not found");
        assertThat(event.anonymousId()).isEqualTo(anonymousId);
        assertThat(event.sessionPublicId()).isEqualTo(sessionId);
        assertThat(event.endpoint()).doesNotContain("secret");
    }

    @Test
    void requestsRejectedBySecurityAreLoggedToo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/topics")).andExpect(status().isUnauthorized());

        ApiLogEvent event = capture();
        assertThat(event.statusCode()).isEqualTo(401);
        assertThat(event.errorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(event.endpoint()).isEqualTo("/api/v1/admin/topics");
    }

    @Test
    void routesWithoutAControllerAreLoggedWithTheirRealPathNotTheCatchAllPattern() throws Exception {
        // /api/v1/media/** is a public area without a controller yet, so the request reaches MVC and gets a 404.
        mockMvc.perform(get("/api/v1/media/does-not-exist")).andExpect(status().isNotFound());

        ApiLogEvent event = capture();
        assertThat(event.endpoint()).isEqualTo("/api/v1/media/does-not-exist");
        assertThat(event.statusCode()).isEqualTo(404);
        assertThat(event.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void validationFailuresAreLoggedAsClientErrors() throws Exception {
        mockMvc.perform(post("/api/v1/game-sessions").contentType("application/json").content("{}")
                        .header("X-Anonymous-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        ApiLogEvent event = capture();
        assertThat(event.httpMethod()).isEqualTo("POST");
        assertThat(event.statusCode()).isEqualTo(400);
        assertThat(event.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(event.requestSizeBytes()).isEqualTo(2);
    }

    @Test
    void malformedAnonymousIdIsNotRecordedAsAPlayer() throws Exception {
        mockMvc.perform(get("/api/v1/game-sessions/" + UUID.randomUUID()).header("X-Anonymous-Id", "garbage"))
                .andExpect(status().isBadRequest());

        assertThat(capture().anonymousId()).isNull();
    }

    @Test
    void nonApiPathsAreNotLogged() throws Exception {
        mockMvc.perform(get("/something-else")).andExpect(status().isUnauthorized());

        verify(apiLogService, never()).record(any());
    }

    @Test
    void aFailingLogServiceNeverBreaksTheApiResponse() throws Exception {
        when(topicService.getPlayableTopics(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("queue is full")).when(apiLogService).record(any());

        mockMvc.perform(get("/api/v1/topics")).andExpect(status().isOk());
    }

    private ApiLogEvent capture() {
        ArgumentCaptor<ApiLogEvent> captor = ArgumentCaptor.forClass(ApiLogEvent.class);
        verify(apiLogService).record(captor.capture());
        return captor.getValue();
    }
}
