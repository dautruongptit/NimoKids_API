package com.nimokids.exception;

/** The selected option id does not exist. */
public class InvalidOptionException extends BusinessException {

    public InvalidOptionException() {
        super(ErrorCode.INVALID_OPTION);
    }
}
