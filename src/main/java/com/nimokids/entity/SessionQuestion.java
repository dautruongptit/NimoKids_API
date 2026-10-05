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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Question selected for a specific session. Separate from game_questions to keep history consistent. */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private AnswerResult result;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "answered_at")
    private Instant answeredAt;
}
