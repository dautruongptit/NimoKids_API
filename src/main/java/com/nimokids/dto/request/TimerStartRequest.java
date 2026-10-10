package com.nimokids.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POST /api/v1/game-sessions/{sessionId}/timer-start. The client reports that the question audio ended, i.e. that
 * the countdown has started. The first report wins; the server also caps how late it can be.
 * {@code restart = true} is sent after "Listen again": the countdown starts over from the moment of that call.
 */
public record TimerStartRequest(@NotNull(message = "must not be null") UUID questionId, Boolean restart) {

    public TimerStartRequest(UUID questionId) {
        this(questionId, null);
    }
}
