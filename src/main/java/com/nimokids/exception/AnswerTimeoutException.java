package com.nimokids.exception;

/**
 * Raised when an answer is submitted for a question that has already been recorded as TIMEOUT (BR-010).
 * A first answer that merely arrives after the deadline is NOT an error: it is recorded with result TIMEOUT.
 */
public class AnswerTimeoutException extends BusinessException {

    public AnswerTimeoutException() {
        super(ErrorCode.ANSWER_TIMEOUT);
    }
}
