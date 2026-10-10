package com.nimokids.dto.request;

/** POST /auth/logout-all. keepCurrent = leave this device signed in. */
public record LogoutAllRequest(boolean keepCurrent) {
}
