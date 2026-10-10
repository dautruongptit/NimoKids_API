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
    INSUFFICIENT_DISTRACTORS(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough matching answers to build the options"),
    INVALID_DISTRACTOR_RULES(HttpStatus.UNPROCESSABLE_ENTITY, "The distractor rules of the question are invalid"),
    OPTION_NOT_BELONG_TO_QUESTION(HttpStatus.UNPROCESSABLE_ENTITY, "Option does not belong to the question"),
    ANSWER_TIMEOUT(HttpStatus.CONFLICT, "Question has already timed out"),
    DUPLICATE_ANSWER(HttpStatus.CONFLICT, "Duplicate or concurrent answer"),
    ANONYMOUS_PLAYER_REQUIRED(HttpStatus.BAD_REQUEST, "A valid X-Anonymous-Id header is required"),
    // Not in the master list: needed for the Admin JWT protection (401 / 403) of /api/v1/admin/**.
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    // Authentication, sessions and tokens (docs 06-authentication, section 6 of 02-FLOWS).
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The access token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The access token is not valid"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "The session has expired"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "The session has ended"),
    REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "The refresh token is not valid"),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "The refresh token was already used; the session has been ended"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "The account is not available"),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "The request origin is not allowed"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later"),
    AUTH_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session not found"),
    RETURN_TO_INVALID(HttpStatus.BAD_REQUEST, "The return address is not allowed"),
    OAUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in with Google is not available"),
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
