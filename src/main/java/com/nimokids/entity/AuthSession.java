package com.nimokids.entity;

import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.AuthSessionStatus;
import com.nimokids.entity.enums.SessionEndReason;
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

/**
 * One login (user or admin). One session is one refresh-token family. Timestamps are set by the application from the
 * injected Clock (never by the database), so expiry logic is deterministic in tests.
 *
 * <pre>
 *   idleExpiresAt      planned idle end, moves forward with activity, never beyond absoluteExpiresAt
 *   absoluteExpiresAt  planned absolute end, FIXED at login
 *   endedAt/endedReason  what actually happened
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "admin_user_id", updatable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, updatable = false, length = 15)
    private AuthMethod authMethod;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AuthSessionStatus status = AuthSessionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", length = 20)
    private SessionEndReason endedReason;

    @Column(name = "revoked_by_admin_id")
    private UUID revokedByAdminId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "login_ip", columnDefinition = "inet", updatable = false)
    private InetAddress loginIp;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "last_ip", columnDefinition = "inet")
    private InetAddress lastIp;

    @Builder.Default
    @Column(name = "ip_change_count", nullable = false)
    private int ipChangeCount = 0;

    @Column(name = "user_agent_login", length = 300, updatable = false)
    private String userAgentLogin;

    @Column(name = "user_agent_last", length = 300)
    private String userAgentLast;

    @Column(name = "browser", length = 60)
    private String browser;

    @Column(name = "os", length = 60)
    private String os;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "device_id_hash")
    private byte[] deviceIdHash;

    @Column(name = "login_history_id")
    private UUID loginHistoryId;

    /** The policy values applied when the session was created, so a later settings change stays explainable. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> policySnapshot = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAdminSession() {
        return adminUserId != null;
    }

    public UUID principalId() {
        return adminUserId != null ? adminUserId : userId;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AuthSession other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return AuthSession.class.hashCode();
    }
}
