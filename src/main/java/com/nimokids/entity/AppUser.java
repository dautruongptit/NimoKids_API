package com.nimokids.entity;

import com.nimokids.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A back-office account (Admin portal). Players are anonymous and are never stored here. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** bcrypt hash. Never returned by the API and never logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Keep the hash out of any accidental toString/log output. */
    @Override
    public String toString() {
        return "AppUser(id=" + getId() + ", username=" + username + ", role=" + role + ")";
    }
}
