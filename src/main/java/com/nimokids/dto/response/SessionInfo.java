package com.nimokids.dto.response;

import java.time.Instant;

/** The limits of the current session: when it must end at the latest, and when it ends if nothing happens. */
public record SessionInfo(String id, Instant expiresAt, Instant idleExpiresAt) {
}
