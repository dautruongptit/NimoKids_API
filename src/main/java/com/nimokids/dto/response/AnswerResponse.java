package com.nimokids.dto.response;

import com.nimokids.entity.enums.AnswerResult;

/**
 * Result of POST .../submit-answer and POST .../timeout. Every value is decided by the server.
 * "feedback" may be null (e.g. on timeout). "nextQuestion" is null after the last question
 * (hasNextQuestion = false); fetch the final result from GET .../result.
 */
public record AnswerResponse(
        AnswerResult result,
        boolean correct,
        int score,
        int currentStreak,
        CorrectAnswerResponse correctAnswer,
        FeedbackResponse feedback,
        boolean hasNextQuestion,
        NextQuestionResponse nextQuestion) {
}
