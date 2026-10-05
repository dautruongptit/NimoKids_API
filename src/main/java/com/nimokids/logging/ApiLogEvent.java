package com.nimokids.logging;

import java.util.UUID;

/**
 * Everything the API log needs, captured on the request thread. Contains no headers, no body and no query
 * string, so credentials (Authorization, Cookie, passwords) can never reach the log.
 *
 * @param endpoint        the matched route pattern (e.g. /api/v1/game-sessions/{sessionId}/answers)
 * @param anonymousId     value of X-Anonymous-Id when it is a valid UUID, otherwise null
 * @param sessionPublicId the {sessionId} path variable (game_sessions.session_id) when present
 */
public record ApiLogEvent(
        UUID requestId,
        String httpMethod,
        String endpoint,
        int statusCode,
        long responseTimeMs,
        String ipAddress,
        String userAgent,
        Integer requestSizeBytes,
        Integer responseSizeBytes,
        String errorCode,
        String errorMessage,
        UUID anonymousId,
        UUID sessionPublicId) {
}
