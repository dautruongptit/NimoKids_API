package com.nimokids.dto.request;

import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.entity.enums.AgeGroup;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POST /api/v1/game-sessions. The player is identified by the X-Anonymous-Id header, never by the body.
 *
 * @param topicId  null for MIX mode (questions drawn from all root topics)
 * @param ageGroup null defaults to AGE_1_3
 * @param languageMode EN, VI or VI_EN; null defaults to EN (older clients)
 */
public record CreateGameSessionRequest(
        UUID topicId,
        @NotNull(message = "must not be null") UUID gameModeId,
        AgeGroup ageGroup,
        LanguageMode languageMode) {

    /** Pre-language-mode callers: plays in EN. */
    public CreateGameSessionRequest(UUID topicId, UUID gameModeId, AgeGroup ageGroup) {
        this(topicId, gameModeId, ageGroup, null);
    }

    public AgeGroup resolvedAgeGroup() {
        return ageGroup != null ? ageGroup : AgeGroup.AGE_1_3;
    }

    public LanguageMode resolvedLanguageMode() {
        return languageMode != null ? languageMode : LanguageMode.EN;
    }
}
