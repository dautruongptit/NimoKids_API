package com.nimokids.entity;

import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.LoginOutcome;
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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One sign-in attempt. Append-only: the database rejects UPDATE and DELETE (V12). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "admin_user_id", updatable = false)
    private UUID adminUserId;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 15)
    private AuthMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private LoginOutcome outcome;

    @Column(name = "failure_code", length = 40, updatable = false)
    private String failureCode;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet", updatable = false)
    private InetAddress ip;

    @Column(name = "country", length = 2, updatable = false)
    private String country;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    @Column(name = "browser", length = 60, updatable = false)
    private String browser;

    @Column(name = "os", length = 60, updatable = false)
    private String os;

    @Column(name = "device_type", length = 20, updatable = false)
    private String deviceType;

    @Column(name = "device_id_hash", updatable = false)
    private byte[] deviceIdHash;

    @Column(name = "is_new_device", nullable = false, updatable = false)
    private boolean newDevice;

    @Column(name = "is_new_ip", nullable = false, updatable = false)
    private boolean newIp;

    /** A masked e-mail for failed attempts (never a password). */
    @Column(name = "identifier_hint", length = 120, updatable = false)
    private String identifierHint;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;
}
