package com.nimokids.exception;

/** The distractor rules of a question cannot supply enough distinct, matching answer items. */
public class InsufficientDistractorsException extends BusinessException {

    public InsufficientDistractorsException(String message) {
        super(ErrorCode.INSUFFICIENT_DISTRACTORS, message);
    }
}
