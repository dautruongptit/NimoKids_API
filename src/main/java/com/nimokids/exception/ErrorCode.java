package com.nimokids.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes (project_master_context.md section 6.5).
 * Clients must rely on the code, never on the message wording.
 * HTTP status follows section 6.6: 400 bad format, 404 missing, 409 state/concurrency, 422 business validation.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    TOPIC_NOT_PLAYABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Topic is not playable"),
    GAME_MODE_NOT_PLAYABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Game mode is not playable"),
    INSUFFICIENT_QUESTIONS(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough playable questions"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Game session not found"),
    SESSION_NOT_ACTIVE(HttpStatus.CONFLICT, "Game session is not active"),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Game session has already been completed"),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Question not found"),
    QUESTION_ALREADY_ANSWERED(HttpStatus.CONFLICT, "Question has already been answered"),
    INVALID_OPTION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid option"),
    OPTION_NOT_BELONG_TO_QUESTION(HttpStatus.UNPROCESSABLE_ENTITY, "Option does not belong to the question"),
    ANSWER_TIMEOUT(HttpStatus.CONFLICT, "Question has already timed out"),
    DUPLICATE_ANSWER(HttpStatus.CONFLICT, "Duplicate or concurrent answer"),
    ANONYMOUS_PLAYER_REQUIRED(HttpStatus.BAD_REQUEST, "A valid X-Anonymous-Id header is required"),
    // Not in the master list: needed for the Admin JWT protection (401 / 403) of /api/v1/admin/**.
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
