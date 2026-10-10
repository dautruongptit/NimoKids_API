package com.nimokids.dto.response;

import java.time.Instant;

/** One of the caller's own sessions (My devices). The IP is masked. */
public record SessionSummaryResponse(String id, boolean current, Instant createdAt, Instant lastSeenAt, Instant expiresAt,
                                     String ipMasked, String browser, String os, String deviceType) {
}
