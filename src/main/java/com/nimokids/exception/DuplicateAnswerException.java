package com.nimokids.exception;

/** Concurrent or repeated submission lost the race; only the first request may affect the score. */
public class DuplicateAnswerException extends BusinessException {

    public DuplicateAnswerException() {
        super(ErrorCode.DUPLICATE_ANSWER);
    }
}
