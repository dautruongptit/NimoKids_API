package com.nimokids.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** The per-request check that makes revocation effective before the access token itself expires. */
public interface SessionGuard {

    enum Result { OK, EXPIRED, REVOKED }

    /** OK also records activity (throttled). An unknown session id counts as REVOKED. */
    Result check(UUID sessionId, HttpServletRequest request);
}
