package com.nimokids.exception;

/** Fewer than the required 5 unique playable questions are available for the session. */
public class InsufficientQuestionsException extends BusinessException {

    public InsufficientQuestionsException() {
        super(ErrorCode.INSUFFICIENT_QUESTIONS);
    }
}
