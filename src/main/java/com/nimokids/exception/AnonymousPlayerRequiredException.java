package com.nimokids.exception;

/** The X-Anonymous-Id header is missing or is not a valid UUID. */
public class AnonymousPlayerRequiredException extends BusinessException {

    public AnonymousPlayerRequiredException() {
        super(ErrorCode.ANONYMOUS_PLAYER_REQUIRED);
    }
}
