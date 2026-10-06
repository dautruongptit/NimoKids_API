package com.nimokids.exception;

/** {@code metadata.distractor_rules} of a question is missing or malformed. */
public class InvalidDistractorRulesException extends BusinessException {

    public InvalidDistractorRulesException(String message) {
        super(ErrorCode.INVALID_DISTRACTOR_RULES, message);
    }
}
