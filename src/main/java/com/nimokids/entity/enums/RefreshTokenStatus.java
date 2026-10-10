package com.nimokids.entity.enums;

/** State of one refresh token of a session family. */
public enum RefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    EXPIRED
}
