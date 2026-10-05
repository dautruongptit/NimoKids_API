package com.nimokids.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POST /api/v1/game-sessions/{sessionId}/answers.
 * The client only sends the selected option. Correctness, score, streak and response time are
 * decided by the server, so none of them is accepted here.
 */
public record SubmitAnswerRequest(
        @NotNull(message = "must not be null") UUID questionId,
        @NotNull(message = "must not be null") UUID selectedOptionId) {
}
