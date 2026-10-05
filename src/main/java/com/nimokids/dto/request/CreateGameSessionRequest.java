package com.nimokids.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POST /api/v1/game-sessions. The player is identified by the X-Anonymous-Id header, never by the body.
 *
 * @param age optional age filter (1-5)
 */
public record CreateGameSessionRequest(
        @NotNull(message = "must not be null") UUID topicId,
        @NotNull(message = "must not be null") UUID gameModeId,
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer age) {
}
