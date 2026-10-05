package com.nimokids.service.impl;

import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.ApiLog;
import com.nimokids.entity.GameSession;
import com.nimokids.logging.ApiLogEvent;
import com.nimokids.repository.AnonymousPlayerRepository;
import com.nimokids.repository.ApiLogRepository;
import com.nimokids.repository.GameSessionRepository;
import com.nimokids.service.ApiLogService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogServiceImpl implements ApiLogService {

    private static final int MAX_ENDPOINT = 500;
    private static final int MAX_USER_AGENT = 500;
    private static final int MAX_ERROR_MESSAGE = 500;
    /** Only IP literals are parsed, so a hostile header can never trigger a DNS lookup. */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final ApiLogRepository apiLogRepository;
    private final AnonymousPlayerRepository playerRepository;
    private final GameSessionRepository sessionRepository;

    /**
     * Runs on the "apiLogExecutor" pool. The lookups and the insert are deliberately not one transaction:
     * a duplicate request id is expected when a client reuses X-Request-Id and must only drop this one row.
     */
    @Override
    @Async("apiLogExecutor")
    public void record(ApiLogEvent event) {
        try {
            AnonymousPlayer player = event.anonymousId() == null
                    ? null
                    : playerRepository.findByAnonymousId(event.anonymousId()).orElse(null);
            GameSession session = event.sessionPublicId() == null
                    ? null
                    : sessionRepository.findBySessionId(event.sessionPublicId()).orElse(null);

            apiLogRepository.save(ApiLog.builder()
                    .requestId(event.requestId())
                    .player(player)
                    .session(session)
                    .httpMethod(truncate(event.httpMethod(), 10))
                    .endpoint(truncate(event.endpoint(), MAX_ENDPOINT))
                    .statusCode((short) event.statusCode())
                    .responseTimeMs((int) Math.min(Integer.MAX_VALUE, event.responseTimeMs()))
                    .ipAddress(parseIp(event.ipAddress()))
                    .userAgent(truncate(event.userAgent(), MAX_USER_AGENT))
                    .requestSizeBytes(event.requestSizeBytes())
                    .responseSizeBytes(event.responseSizeBytes())
                    .errorCode(truncate(event.errorCode(), 100))
                    .errorMessage(truncate(event.errorMessage(), MAX_ERROR_MESSAGE))
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("API log skipped, request id {} was already logged", event.requestId());
        } catch (RuntimeException ex) {
            log.warn("Could not store API log: {}", ex.getClass().getSimpleName());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static InetAddress parseIp(String value) {
        if (value == null || !IP_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
