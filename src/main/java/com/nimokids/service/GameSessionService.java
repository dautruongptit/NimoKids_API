package com.nimokids.service;

import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import java.util.UUID;

/**
 * Source of truth for game rules: question selection, answer validation, timeout, score, streak,
 * session lifecycle and ownership. The frontend only sends the selected option.
 *
 * Every method takes the anonymous id from the X-Anonymous-Id header. A session that belongs to
 * another player is reported as SESSION_NOT_FOUND.
 */
public interface GameSessionService {

    /** Starts a new session with 5 unique random playable questions (always a NEW session, also for Play Again). */
    GameSessionResponse createSession(UUID anonymousId, CreateGameSessionRequest request);

    /** Session state plus the current question, so a refreshed browser can resume a STARTED session. */
    GameSessionResponse getSession(UUID anonymousId, UUID sessionId);

    AnswerResponse submitAnswer(UUID anonymousId, UUID sessionId, SubmitAnswerRequest request);

    AnswerResponse submitTimeout(UUID anonymousId, UUID sessionId, SubmitTimeoutRequest request);

    /** Recovery endpoint: returns the result of a completed session (idempotent). */
    GameResultResponse finishSession(UUID anonymousId, UUID sessionId);

    GameResultResponse getResult(UUID anonymousId, UUID sessionId);

    /** Marks STARTED sessions without activity for 30 minutes as ABANDONED. Returns how many were changed. */
    int abandonInactiveSessions();
}
