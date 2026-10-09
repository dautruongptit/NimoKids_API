package com.nimokids.controller;

import com.nimokids.entity.enums.AgeGroup;
import com.nimokids.entity.enums.LanguageMode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.config.SecurityConfig;
import com.nimokids.dto.request.ActivityBatchRequest;
import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.request.TimerStartRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.CorrectAnswerResponse;
import com.nimokids.dto.response.FeedbackResponse;
import com.nimokids.dto.response.GameModeResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.dto.response.NextQuestionResponse;
import com.nimokids.dto.response.OptionResponse;
import com.nimokids.dto.response.QuestionResponse;
import com.nimokids.dto.response.StickerResponse;
import com.nimokids.dto.response.TopicResponse;
import com.nimokids.dto.response.TopicSummaryResponse;
import com.nimokids.entity.enums.AnswerResult;
import com.nimokids.entity.enums.SessionStatus;
import com.nimokids.entity.enums.StickerRarity;
import com.nimokids.exception.QuestionAlreadyAnsweredException;
import com.nimokids.exception.SessionNotFoundException;
import com.nimokids.security.JwtService;
import com.nimokids.security.SecurityErrorHandler;
import com.nimokids.security.StaticRoleAuthorityResolver;
import com.nimokids.service.ActivityLogService;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.GameModeService;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.StickerService;
import com.nimokids.service.TopicService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        TopicController.class, GameModeController.class, GameSessionController.class,
        PlayerController.class, ActivityController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class})
class ApiControllersTest {

    private static final String ANONYMOUS_HEADER = "X-Anonymous-Id";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TopicService topicService;
    @MockitoBean private GameModeService gameModeService;
    @MockitoBean private GameSessionService gameSessionService;
    @MockitoBean private StickerService stickerService;
    @MockitoBean private ActivityLogService activityLogService;
    @MockitoBean private ApiLogService apiLogService;

    private final UUID anonymousId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID optionId = UUID.randomUUID();
    private final UUID topicId = UUID.randomUUID();
    private final UUID modeId = UUID.randomUUID();

    // ------------------------------------------------------------ envelope

    @Test
    void topicsAreReturnedInTheStandardEnvelopeAndEchoTheRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(topicService.getPlayableTopics(LanguageMode.EN)).thenReturn(List.of(new TopicResponse(
                topicId, null, "ANIMALS", "Animals", "animals", null, null, null, 1, 5, 1)));

        mockMvc.perform(get("/api/v1/topics").header("X-Request-Id", requestId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("ANIMALS"));
    }

    @Test
    void requestIdIsGeneratedWhenMissingOrMalformed() throws Exception {
        when(gameModeService.getActiveGameModes()).thenReturn(List.of(new GameModeResponse(modeId, "GUESS", "Guess", null)));

        mockMvc.perform(get("/api/v1/game-modes").header("X-Request-Id", "not-a-uuid\r\ninjected"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].code").value("GUESS"));
    }

    // -------------------------------------------------------- game session

    @Test
    void createSessionReturns201AndPassesThePlayerFromTheHeader() throws Exception {
        when(gameSessionService.createSession(eq(anonymousId), any())).thenReturn(sessionResponse());

        mockMvc.perform(post("/api/v1/game-sessions")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\",\"gameModeId\":\"" + modeId + "\",\"ageGroup\":\"AGE_4_5\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.status").value("STARTED"));

        verify(gameSessionService).createSession(anonymousId, new CreateGameSessionRequest(topicId, modeId, AgeGroup.AGE_4_5));
    }

    @Test
    void anonymousIdBodyFieldIsIgnoredBecauseThePlayerComesFromTheHeaderOnly() throws Exception {
        UUID spoofed = UUID.randomUUID();
        when(gameSessionService.createSession(any(), any())).thenReturn(sessionResponse());

        mockMvc.perform(post("/api/v1/game-sessions")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"" + spoofed + "\",\"topicId\":\"" + topicId
                                + "\",\"gameModeId\":\"" + modeId + "\"}"))
                .andExpect(status().isCreated());

        verify(gameSessionService).createSession(eq(anonymousId), any());
    }

    @Test
    void missingAnonymousIdHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("ANONYMOUS_PLAYER_REQUIRED"));
    }

    @Test
    void malformedAnonymousIdHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId).header(ANONYMOUS_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANONYMOUS_PLAYER_REQUIRED"));
    }

    @Test
    void createSessionValidatesTheBody() throws Exception {
        mockMvc.perform(post("/api/v1/game-sessions")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='gameModeId')]").exists())
                ;
    }

    @Test
    void getSessionDelegatesToTheService() throws Exception {
        when(gameSessionService.getSession(anonymousId, sessionId)).thenReturn(sessionResponse());

        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId).header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(5));
    }

    @Test
    void invalidSessionIdInThePathIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/game-sessions/not-a-uuid").header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("sessionId"));
    }

    @Test
    void submitAnswerOnlyAcceptsTheSelectedOptionAndIgnoresClientScoreAndCorrectness() throws Exception {
        when(gameSessionService.submitAnswer(eq(anonymousId), eq(sessionId), any())).thenReturn(
                new AnswerResponse(AnswerResult.CORRECT, true, 1, 1,
                        new CorrectAnswerResponse(optionId, "Cat", null), new FeedbackResponse(null, "Great job!"), true,
                        new NextQuestionResponse(2, 5, new QuestionResponse(questionId, "Which animal can fly?", null, null, null,
                                List.of(new OptionResponse(optionId, "Bird", null, null))))));

        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/submit-answer")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"selectedOptionId\":\"" + optionId
                                + "\",\"isCorrect\":true,\"score\":5,\"responseTimeMs\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("CORRECT"))
                .andExpect(jsonPath("$.data.hasNextQuestion").value(true))
                .andExpect(jsonPath("$.data.nextQuestion.questionNumber").value(2))
                .andExpect(jsonPath("$.data.nextQuestion.timeLimitSeconds").value(5))
                .andExpect(jsonPath("$.data.nextQuestion.question.options[0].text").value("Bird"))
                .andExpect(jsonPath("$.data.nextQuestion.question.options[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.data.nextQuestion.question.options[0].correct").doesNotExist());

        // The master-document path is kept as an alias of the same endpoint.
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/answers")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"selectedOptionId\":\"" + optionId + "\"}"))
                .andExpect(status().isOk());

        verify(gameSessionService, org.mockito.Mockito.times(2))
                .submitAnswer(anonymousId, sessionId, new SubmitAnswerRequest(questionId, optionId));
    }

    @Test
    void timerStartIsReportedByTheClientWithOnlyTheQuestionId() throws Exception {
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/timer-start")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Timer started"));

        verify(gameSessionService).startTimer(anonymousId, sessionId, new TimerStartRequest(questionId));
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/timer-start")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='questionId')]").exists());
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/timer-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANONYMOUS_PLAYER_REQUIRED"));
    }

    @Test
    void submitAnswerRequiresQuestionAndOption() throws Exception {
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/answers")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='selectedOptionId')].message").value("must not be null"));
    }

    @Test
    void timeoutFinishAndResultDelegateToTheService() throws Exception {
        when(gameSessionService.submitTimeout(anonymousId, sessionId, new SubmitTimeoutRequest(questionId)))
                .thenReturn(new AnswerResponse(AnswerResult.TIMEOUT, false, 0, 0, null, null, true, null));
        GameResultResponse result = new GameResultResponse(sessionId, "Animals", 5, 4, 0, 1, 4, 80.0, 3, 3, List.of());
        when(gameSessionService.finishSession(anonymousId, sessionId)).thenReturn(result);
        when(gameSessionService.getResult(anonymousId, sessionId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/timeout")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("TIMEOUT"));
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/finish")
                        .header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accuracy").value(80.0));
        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId + "/result")
                        .header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxStreak").value(3));
    }

    // ------------------------------------------------------ business errors

    @Test
    void businessExceptionsKeepTheirStableCodeAndStatus() throws Exception {
        when(gameSessionService.getSession(anonymousId, sessionId)).thenThrow(new SessionNotFoundException());
        when(gameSessionService.submitAnswer(eq(anonymousId), eq(sessionId), any()))
                .thenThrow(new QuestionAlreadyAnsweredException());

        mockMvc.perform(get("/api/v1/game-sessions/" + sessionId).header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/game-sessions/" + sessionId + "/answers")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"selectedOptionId\":\"" + optionId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("QUESTION_ALREADY_ANSWERED"));
    }

    // ------------------------------------------------- players, activities

    @Test
    void stickersAreResolvedFromTheHeaderNotTheUrl() throws Exception {
        when(stickerService.getPlayerStickers(anonymousId)).thenReturn(List.of(
                new StickerResponse("FIRST_GAME", "First Game", null, StickerRarity.COMMON, Instant.parse("2026-10-05T14:00:00Z"))));

        mockMvc.perform(get("/api/v1/players/me/stickers").header(ANONYMOUS_HEADER, anonymousId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("FIRST_GAME"))
                .andExpect(jsonPath("$.data[0].rarity").value("COMMON"));
        mockMvc.perform(get("/api/v1/players/me/stickers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANONYMOUS_PLAYER_REQUIRED"));
    }

    @Test
    void activityBatchIsValidatedAndDelegated() throws Exception {
        mockMvc.perform(post("/api/v1/activities/batch")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='events')]").exists());

        mockMvc.perform(post("/api/v1/activities/batch")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"events\":[{\"eventType\":\"VIEW_QUESTION\","
                                + "\"questionId\":\"" + questionId + "\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Activities recorded"));

        verify(activityLogService).recordBatch(eq(anonymousId), any(ActivityBatchRequest.class));
    }

    @Test
    void unknownActivityEventTypeIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/activities/batch")
                        .header(ANONYMOUS_HEADER, anonymousId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventType\":\"HACK\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------- routing, security, CORS

    @Test
    void unknownRouteInsideAPublicAreaIsResourceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/topics/a/b/c/d"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routesThatAreNotOnThePublicListAreDeniedInsteadOfLeakingAs404() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void unsupportedMethodIsAClientErrorInTheEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/game-sessions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void everythingOutsideTheApiIsDenied() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void corsPreflightIsAllowedForTheConfiguredFrontendOnly() throws Exception {
        mockMvc.perform(options("/api/v1/game-sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-anonymous-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/game-sessions")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private GameSessionResponse sessionResponse() {
        return new GameSessionResponse(sessionId, SessionStatus.STARTED, new TopicSummaryResponse(topicId, "Animals"),
                5, 1, 5, null);
    }
}
