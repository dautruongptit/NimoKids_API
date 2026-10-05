package com.nimokids.entity;

import com.nimokids.entity.enums.SessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One play session (5 questions in the MVP).
 * current_streak / max_streak are not in project-context.md but are required by
 * project_master_context.md (sections 5.7 and 5.10).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "game_sessions")
public class GameSession extends BaseEntity {

    /** Public identifier exposed through the API. */
    @Builder.Default
    @Column(name = "session_id", nullable = false, unique = true, updatable = false)
    private UUID sessionId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private AnonymousPlayer player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, updatable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_mode_id", nullable = false, updatable = false)
    private GameMode gameMode;

    @Builder.Default
    @Column(name = "total_questions", nullable = false)
    private Short totalQuestions = 5;

    @Builder.Default
    @Column(name = "current_question_number", nullable = false)
    private Short currentQuestionNumber = 1;

    @Builder.Default
    @Column(name = "correct_answers", nullable = false)
    private Short correctAnswers = 0;

    @Builder.Default
    @Column(name = "wrong_answers", nullable = false)
    private Short wrongAnswers = 0;

    @Builder.Default
    @Column(name = "timeout_answers", nullable = false)
    private Short timeoutAnswers = 0;

    @Builder.Default
    @Column(name = "score", nullable = false)
    private Short score = 0;

    @Builder.Default
    @Column(name = "current_streak", nullable = false)
    private Short currentStreak = 0;

    @Builder.Default
    @Column(name = "max_streak", nullable = false)
    private Short maxStreak = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.STARTED;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionNumber ASC")
    private List<SessionQuestion> sessionQuestions = new ArrayList<>();

    public void addSessionQuestion(SessionQuestion sessionQuestion) {
        sessionQuestions.add(sessionQuestion);
        sessionQuestion.setSession(this);
    }
}
