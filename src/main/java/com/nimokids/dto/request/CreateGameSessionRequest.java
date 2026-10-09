package com.nimokids.dto.request;

import com.nimokids.entity.enums.AgeGroup;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POST /api/v1/game-sessions. The player is identified by the X-Anonymous-Id header, never by the body.
 *
 * @param topicId  null for MIX mode (questions drawn from all root topics)
 * @param ageGroup null defaults to AGE_1_3
 */
public record CreateGameSessionRequest(
        UUID topicId,
        @NotNull(message = "must not be null") UUID gameModeId,
        AgeGroup ageGroup) {

    public AgeGroup resolvedAgeGroup() {
        return ageGroup != null ? ageGroup : AgeGroup.AGE_1_3;
    }
}
