package com.nimokids.exception;

/**
 * The session does not exist OR belongs to another player. Both cases deliberately look the same
 * so that session ids cannot be probed (business-rules BR-029, section 17).
 */
public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException() {
        super(ErrorCode.SESSION_NOT_FOUND);
    }
}
