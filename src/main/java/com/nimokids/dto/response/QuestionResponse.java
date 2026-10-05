package com.nimokids.dto.response;

import java.util.List;
import java.util.UUID;

/** Media fields are URLs resolved from media_assets. objectSound is only set for sound-based questions. */
public record QuestionResponse(
        UUID id,
        String questionText,
        String questionVoice,
        String objectSound,
        List<OptionResponse> options) {
}
