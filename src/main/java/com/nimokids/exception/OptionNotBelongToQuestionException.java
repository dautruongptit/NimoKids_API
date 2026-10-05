package com.nimokids.exception;

/** The selected option exists but belongs to a different question (BR-012). */
public class OptionNotBelongToQuestionException extends BusinessException {

    public OptionNotBelongToQuestionException() {
        super(ErrorCode.OPTION_NOT_BELONG_TO_QUESTION);
    }
}
