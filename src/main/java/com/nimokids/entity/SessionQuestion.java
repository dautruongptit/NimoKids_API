package com.nimokids.entity;

import com.nimokids.entity.enums.AnswerResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Question selected for a specific session, with the options generated for it. Separate from game_questions so the
 * history stays consistent when the question bank or the vocabulary changes later.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "session_questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_session_questions_session_number",
                        columnNames = {"session_id", "question_number"}),
                @UniqueConstraint(
                        name = "uk_session_questions_session_question",
                        columnNames = {"session_id", "question_id"})
        })
public class SessionQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private GameQuestion question;

    @Column(name = "question_number", nullable = false, updatable = false)
    private Short questionNumber;

    /** The 4 generated, shuffled options. Immutable evidence for scoring (never regenerate or edit). */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<SnapshotOption> optionsSnapshot = new ArrayList<>();

    /** The question as the child saw it (text, voice, image, topic, age group, generation rules). Null before V9. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot", updatable = false, columnDefinition = "jsonb")
    private QuestionSnapshot questionSnapshot;

    /** When the server handed this question to the client. */
    @Column(name = "presented_at")
    private Instant presentedAt;

    /** When the client reported that the question audio ended (the countdown starts then). */
    @Column(name = "timer_started_at")
    private Instant timerStartedAt;

    /** Set when the child pressed "Listen again": the countdown restarted at this moment (server clock). */
    @Column(name = "timer_restarted_at")
    private Instant timerRestartedAt;

    /** How many times "Listen again" restarted the countdown for this question (capped by GameConstants.MAX_LISTEN_AGAIN). */
    @Column(name = "timer_restarts", nullable = false)
    private int timerRestarts;

    /** An optionId from {@link #optionsSnapshot}. A plain UUID: there is no options table to point at any more. */
    @Column(name = "selected_option_id")
    private UUID selectedOptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private AnswerResult result;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "answered_at")
    private Instant answeredAt;
}
