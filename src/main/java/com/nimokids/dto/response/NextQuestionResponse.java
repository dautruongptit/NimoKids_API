package com.nimokids.dto.response;

/**
 * The question the child should see next, delivered inside the answer/timeout response so the frontend
 * does not need an extra "next question" call. Never contains the correct option.
 */
public record NextQuestionResponse(Integer questionNumber, Integer timeLimitSeconds, QuestionResponse question) {
}
