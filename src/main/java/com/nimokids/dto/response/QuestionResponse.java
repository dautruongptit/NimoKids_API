package com.nimokids.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Media fields are URLs resolved from media_assets. objectSound is only set for sound-based questions.
 * questionImage is the emoji (or later a URL) of the item the question is asking about — used as the
 * large visual the child sees above the answer options. It comes from the correct answer item's metadata
 * and is safe to include: it identifies the subject of the question, not which option card to tap.
 */
public record QuestionResponse(
        UUID id,
        String questionText,
        /** Vietnamese text of the question, a subtitle shown under it in the "learn English" mode (VI_EN); null otherwise. */
        String questionTextVi,
        String questionVoice,
        String objectSound,
        /** Emoji or image URL representing the subject of the question (e.g. "🐱" for a question about cats). */
        String questionImage,
        List<OptionResponse> options) {
}
