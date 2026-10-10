package com.nimokids.entity;

import com.nimokids.entity.enums.UserStatus;
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

/**
 * An end-user account (the parent). Optional: playing stays anonymous. The identity key is the provider subject in
 * {@link OAuthAccount}, never the e-mail, which is only a snapshot of what the provider said.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "locale", length = 10)
    private String locale;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_reason", length = 200)
    private String disabledReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
