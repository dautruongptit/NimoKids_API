package com.nimokids.dto.response;

import java.util.List;
import java.util.UUID;

/** Final result of a session (project_master_context.md section 5.10). Accuracy is a percentage 0-100. */
public record GameResultResponse(
        UUID sessionId,
        String topic,
        int totalQuestions,
        int correctAnswers,
        int wrongAnswers,
        int timeoutAnswers,
        int score,
        double accuracy,
        int currentStreak,
        int maxStreak,
        List<StickerResponse> earnedStickers) {
}
