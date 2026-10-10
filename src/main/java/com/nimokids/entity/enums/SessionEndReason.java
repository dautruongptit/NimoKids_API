package com.nimokids.entity.enums;

/** Why a login session ended (user_sessions.ended_reason). */
public enum SessionEndReason {
    IDLE,
    ABSOLUTE,
    LOGOUT,
    LOGOUT_ALL,
    ADMIN_REVOKED,
    ACCOUNT_DISABLED,
    REFRESH_REUSE,
    EVICTED,
    POLICY_CHANGED
}
