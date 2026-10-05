package com.nimokids.exception;

/** A question can be answered only once, including after CORRECT, WRONG or TIMEOUT (BR-011). */
public class QuestionAlreadyAnsweredException extends BusinessException {

    public QuestionAlreadyAnsweredException() {
        super(ErrorCode.QUESTION_ALREADY_ANSWERED);
    }
}
