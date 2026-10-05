package com.nimokids.dto.response;

import com.nimokids.entity.enums.SessionStatus;
import java.util.UUID;

/**
 * Session state plus the question to show now. Used by create session, get session and
 * current/next question. "question" is null once the session is no longer STARTED.
 */
public record GameSessionResponse(
        UUID sessionId,
        SessionStatus status,
        TopicSummaryResponse topic,
        Integer totalQuestions,
        Integer currentQuestionNumber,
        Integer timeLimitSeconds,
        QuestionResponse question) {
}
