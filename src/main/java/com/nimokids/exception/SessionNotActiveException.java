package com.nimokids.exception;

/** The session is not in STARTED state (e.g. ABANDONED). */
public class SessionNotActiveException extends BusinessException {

    public SessionNotActiveException() {
        super(ErrorCode.SESSION_NOT_ACTIVE);
    }
}
