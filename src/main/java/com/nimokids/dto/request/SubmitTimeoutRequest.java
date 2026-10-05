package com.nimokids.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** POST /api/v1/game-sessions/{sessionId}/timeout. */
public record SubmitTimeoutRequest(@NotNull(message = "must not be null") UUID questionId) {
}
