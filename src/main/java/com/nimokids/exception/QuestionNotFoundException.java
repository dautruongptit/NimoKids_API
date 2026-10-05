package com.nimokids.exception;

/** The question does not exist, is not part of the given session, or is not the current question. */
public class QuestionNotFoundException extends BusinessException {

    public QuestionNotFoundException() {
        super(ErrorCode.QUESTION_NOT_FOUND);
    }

    public QuestionNotFoundException(String message) {
        super(ErrorCode.QUESTION_NOT_FOUND, message);
    }
}
