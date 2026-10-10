package com.nimokids.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A Google login in progress (10 minutes, single use). Only hashes of state and nonce are stored. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "oauth_login_attempts")
public class OAuthLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "state_hash", nullable = false, updatable = false)
    private byte[] stateHash;

    @Column(name = "nonce_hash", nullable = false, updatable = false)
    private byte[] nonceHash;

    /** The PKCE code verifier, encrypted with a key derived from the server secret. */
    @Column(name = "code_verifier_enc", nullable = false, updatable = false)
    private byte[] codeVerifierEnc;

    @Column(name = "return_to", length = 200, updatable = false)
    private String returnTo;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet", updatable = false)
    private InetAddress ip;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
