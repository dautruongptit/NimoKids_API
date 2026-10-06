package com.nimokids.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Master question bank. A question stores ONE correct answer item and, in {@code metadata.distractor_rules}, the rules
 * for the three wrong ones. The 4 options are generated when a session starts; they are never stored per question.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "game_questions")
public class GameQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_voice_id")
    private MediaAsset questionVoice;

    /** Only required for sound-based questions (e.g. ANIMAL_SOUND). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_sound_id")
    private MediaAsset objectSound;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correct_answer_item_id", nullable = false)
    private AnswerItem correctAnswerItem;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "difficulty", nullable = false)
    private Short difficulty;

    @Column(name = "min_age", nullable = false)
    private Short minAge;

    @Column(name = "max_age", nullable = false)
    private Short maxAge;

    @Builder.Default
    @Column(name = "time_limit_seconds", nullable = false)
    private Short timeLimitSeconds = 8;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Contains "distractor_rules": [{"match": {...tags...}, "count": n}, ...]. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
