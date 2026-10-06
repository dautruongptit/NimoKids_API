package com.nimokids.controller;

import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.request.TimerStartRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.service.GameSessionService;
import com.nimokids.validation.AnonymousId;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP concerns only: every rule lives in {@link GameSessionService}. The player comes from X-Anonymous-Id. */
@RestController
@RequestMapping("/api/v1/game-sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GameSessionResponse> createSession(
            @AnonymousId UUID anonymousId, @Valid @RequestBody CreateGameSessionRequest request) {
        return ApiResponse.success(gameSessionService.createSession(anonymousId, request));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<GameSessionResponse> getSession(
            @AnonymousId UUID anonymousId, @PathVariable UUID sessionId) {
        return ApiResponse.success(gameSessionService.getSession(anonymousId, sessionId));
    }

    /** The question audio has ended and the countdown started (the first report wins). */
    @PostMapping("/{sessionId}/timer-start")
    public ApiResponse<Void> startTimer(
            @AnonymousId UUID anonymousId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody TimerStartRequest request) {
        gameSessionService.startTimer(anonymousId, sessionId, request);
        return ApiResponse.success("Timer started", null);
    }

    /**
     * Returns the result AND the next question (when there is one), so the frontend needs no next-question call.
     * "/answers" is kept as an alias because project_master_context.md names the endpoint that way.
     */
    @PostMapping({"/{sessionId}/submit-answer", "/{sessionId}/answers"})
    public ApiResponse<AnswerResponse> submitAnswer(
            @AnonymousId UUID anonymousId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.success(gameSessionService.submitAnswer(anonymousId, sessionId, request));
    }

    @PostMapping("/{sessionId}/timeout")
    public ApiResponse<AnswerResponse> submitTimeout(
            @AnonymousId UUID anonymousId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitTimeoutRequest request) {
        return ApiResponse.success(gameSessionService.submitTimeout(anonymousId, sessionId, request));
    }

    @PostMapping("/{sessionId}/finish")
    public ApiResponse<GameResultResponse> finishSession(
            @AnonymousId UUID anonymousId, @PathVariable UUID sessionId) {
        return ApiResponse.success(gameSessionService.finishSession(anonymousId, sessionId));
    }

    @GetMapping("/{sessionId}/result")
    public ApiResponse<GameResultResponse> getResult(
            @AnonymousId UUID anonymousId, @PathVariable UUID sessionId) {
        return ApiResponse.success(gameSessionService.getResult(anonymousId, sessionId));
    }
}
