package com.nimokids.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An anonymous browser/device identified by the X-Anonymous-Id header. No personal information is stored. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "anonymous_players")
public class AnonymousPlayer extends BaseEntity {

    @Column(name = "anonymous_id", nullable = false, unique = true, updatable = false)
    private UUID anonymousId;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "platform", length = 30)
    private String platform;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Builder.Default
    @Column(name = "total_games", nullable = false)
    private Integer totalGames = 0;

    @Builder.Default
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    @Builder.Default
    @Column(name = "total_correct", nullable = false)
    private Integer totalCorrect = 0;
}
