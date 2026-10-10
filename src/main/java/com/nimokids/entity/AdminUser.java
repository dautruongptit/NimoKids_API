package com.nimokids.entity;

import com.nimokids.entity.enums.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A back-office (Admin portal) account. Players are anonymous and live in {@link AnonymousPlayer};
 * the two tables are intentionally never merged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_users")
public class AdminUser extends BaseEntity {

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** bcrypt hash of the password. Never returned by the API and never logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AdminRole role;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private java.time.Instant lastLoginAt;

    /** Consecutive failed password logins; reset on success. Drives the progressive lockout. */
    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    @Column(name = "locked_until")
    private java.time.Instant lockedUntil;

    /** Keep the hash out of any accidental toString/log output. */
    @Override
    public String toString() {
        return "AdminUser(id=" + getId() + ", role=" + role + ")";
    }
}
