package com.nimokids.exception;

/** No further answers are accepted once the session is COMPLETED. */
public class SessionAlreadyCompletedException extends BusinessException {

    public SessionAlreadyCompletedException() {
        super(ErrorCode.SESSION_ALREADY_COMPLETED);
    }
}
