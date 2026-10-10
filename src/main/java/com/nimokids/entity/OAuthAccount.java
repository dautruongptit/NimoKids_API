package com.nimokids.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Links a user to an identity of an external provider. {@code providerSubject} (Google "sub") is the identity key. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "oauth_accounts")
public class OAuthAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 255)
    private String providerSubject;

    @Column(name = "email_at_link", length = 254)
    private String emailAtLink;

    @Column(name = "email_verified_at_link")
    private Boolean emailVerifiedAtLink;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
