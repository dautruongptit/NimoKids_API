package com.nimokids.service.auth;

import com.nimokids.entity.SecurityEvent;
import com.nimokids.entity.enums.EventActorType;
import com.nimokids.entity.enums.EventCategory;
import com.nimokids.entity.enums.EventResult;
import com.nimokids.entity.enums.EventSeverity;
import com.nimokids.repository.SecurityEventRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes security signals and audit actions. Each event is stored in its OWN transaction, so a failing request still
 * leaves its trace, and a problem while writing an event never fails the request that caused it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    private final SecurityEventRepository repository;
    private final Clock clock;

    /** An event detected by the system (actor SYSTEM). */
    public void security(String type, EventSeverity severity, UUID sessionId, UUID targetUserId, UUID targetAdminId,
                         ClientContext ctx, Map<String, Object> metadata) {
        record(EventCategory.SECURITY, type, severity, EventActorType.SYSTEM, null, targetUserId, targetAdminId,
                sessionId, ctx, EventResult.SUCCESS, metadata);
    }

    /** An action by a user or an admin that must be traceable. */
    public void audit(String type, EventActorType actorType, UUID actorId, UUID targetUserId, UUID targetAdminId,
                      UUID sessionId, ClientContext ctx, EventResult result, Map<String, Object> metadata) {
        record(EventCategory.AUDIT, type, EventSeverity.INFO, actorType, actorId, targetUserId, targetAdminId,
                sessionId, ctx, result, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(EventCategory category, String type, EventSeverity severity, EventActorType actorType, UUID actorId,
                       UUID targetUserId, UUID targetAdminId, UUID sessionId, ClientContext ctx, EventResult result,
                       Map<String, Object> metadata) {
        try {
            repository.save(SecurityEvent.builder()
                    .occurredAt(clock.instant())
                    .category(category)
                    .eventType(type)
                    .severity(severity)
                    .actorType(actorType)
                    .actorId(actorId)
                    .targetUserId(targetUserId)
                    .targetAdminId(targetAdminId)
                    .sessionId(sessionId)
                    .ip(ctx != null ? ctx.ip() : null)
                    .requestId(ctx != null ? ctx.requestId() : null)
                    .result(result)
                    .metadata(MetadataSanitizer.clean(metadata))
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not store security event {}: {}", type, ex.getClass().getSimpleName());
        }
    }
}
