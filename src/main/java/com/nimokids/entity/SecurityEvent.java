package com.nimokids.entity;

import com.nimokids.entity.enums.EventActorType;
import com.nimokids.entity.enums.EventCategory;
import com.nimokids.entity.enums.EventResult;
import com.nimokids.entity.enums.EventSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A security signal or an audited action. Append-only (V12). {@code metadata} never holds secrets. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 10)
    private EventCategory category;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 10)
    private EventSeverity severity = EventSeverity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private EventActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "target_admin_id", updatable = false)
    private UUID targetAdminId;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet", updatable = false)
    private InetAddress ip;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 10)
    private EventResult result = EventResult.SUCCESS;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
