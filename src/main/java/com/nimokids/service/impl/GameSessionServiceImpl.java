package com.nimokids.service.impl;

import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.FeedbackResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.dto.response.NextQuestionResponse;
import com.nimokids.dto.response.StickerResponse;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.QuestionOption;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.ActivityEventType;
import com.nimokids.entity.enums.AnswerResult;
import com.nimokids.entity.enums.SessionStatus;
import com.nimokids.exception.AnswerTimeoutException;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.GameModeNotPlayableException;
import com.nimokids.exception.InsufficientQuestionsException;
import com.nimokids.exception.InvalidOptionException;
import com.nimokids.exception.OptionNotBelongToQuestionException;
import com.nimokids.exception.QuestionAlreadyAnsweredException;
import com.nimokids.exception.QuestionNotFoundException;
import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.exception.SessionAlreadyCompletedException;
import com.nimokids.exception.SessionNotActiveException;
import com.nimokids.exception.SessionNotFoundException;
import com.nimokids.exception.TopicNotPlayableException;
import com.nimokids.mapper.GameMapper;
import com.nimokids.repository.AnonymousPlayerRepository;
import com.nimokids.repository.GameModeRepository;
import com.nimokids.repository.GameQuestionRepository;
import com.nimokids.repository.GameSessionRepository;
import com.nimokids.repository.PlayerStickerRepository;
import com.nimokids.repository.QuestionOptionRepository;
import com.nimokids.repository.TopicRepository;
import com.nimokids.service.ActivityLogService;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.PlayerService;
import com.nimokids.service.StickerService;
import com.nimokids.util.GameConstants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameSessionServiceImpl implements GameSessionService {

    private final GameSessionRepository sessionRepository;
    private final GameQuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final TopicRepository topicRepository;
    private final GameModeRepository gameModeRepository;
    private final AnonymousPlayerRepository playerRepository;
    private final PlayerStickerRepository playerStickerRepository;
    private final PlayerService playerService;
    private final StickerService stickerService;
    private final ActivityLogService activityLogService;
    private final GameMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ create

    /** Session and its 5 session_questions are created in one transaction (master 5.4). */
    @Override
    @Transactional
    public GameSessionResponse createSession(UUID anonymousId, CreateGameSessionRequest request) {
        Instant now = clock.instant();
        AnonymousPlayer player = playerService.resolveOrCreate(anonymousId);

        Topic topic = topicRepository.findById(request.topicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", request.topicId()));
        if (!topic.isActive()) {
            throw new TopicNotPlayableException();
        }
        GameMode mode = gameModeRepository.findById(request.gameModeId())
                .orElseThrow(() -> new ResourceNotFoundException("Game mode", request.gameModeId()));
        if (!mode.isActive()) {
            throw new GameModeNotPlayableException();
        }

        int age = request.age() == null ? 0 : request.age();
        List<UUID> candidateIds = new ArrayList<>(questionRepository.findPlayableIds(topic.getId(), mode.getId(), age));
        if (candidateIds.size() < GameConstants.QUESTIONS_PER_SESSION) {
            throw new InsufficientQuestionsException();
        }

        // The pool holds distinct ids, so the selected questions are unique within the session (BR-005).
        Collections.shuffle(candidateIds, ThreadLocalRandom.current());
        List<UUID> selectedIds = candidateIds.subList(0, GameConstants.QUESTIONS_PER_SESSION);
        Map<UUID, GameQuestion> questionsById = questionRepository.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(GameQuestion::getId, Function.identity()));

        GameSession session = GameSession.builder()
                .player(player)
                .topic(topic)
                .gameMode(mode)
                .totalQuestions((short) GameConstants.QUESTIONS_PER_SESSION)
                .startedAt(now)
                .build();
        for (int i = 0; i < selectedIds.size(); i++) {
            session.addSessionQuestion(SessionQuestion.builder()
                    .question(questionsById.get(selectedIds.get(i)))
                    .questionNumber((short) (i + 1))
                    .build());
        }
        sessionRepository.save(session);

        activityLogService.record(ActivityEventType.START_GAME, player, session, topic, null, null, null);
        return mapper.toSessionResponse(session, questionsById.get(selectedIds.get(0)));
    }

    // -------------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public GameSessionResponse getSession(UUID anonymousId, UUID sessionId) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, false);
        GameQuestion current = session.getStatus() == SessionStatus.STARTED
                ? currentSessionQuestion(session).map(SessionQuestion::getQuestion).orElse(null)
                : null;
        return mapper.toSessionResponse(session, current);
    }

    // ------------------------------------------------------------------ answer

    /**
     * Master 5.8: lock and validate the session, validate player/state/question/option/timeout, decide the result,
     * update session_question, game_session and player statistics, award stickers and write the activity log,
     * all in one transaction. The row lock on the session serializes concurrent requests.
     */
    @Override
    @Transactional
    public AnswerResponse submitAnswer(UUID anonymousId, UUID sessionId, SubmitAnswerRequest request) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        requireStarted(session);
        SessionQuestion current = requireCurrentUnanswered(session, request.questionId());
        QuestionOption selected = resolveOption(current.getQuestion(), request.selectedOptionId());

        Instant now = clock.instant();
        Instant startedAt = questionStartedAt(session, current);
        boolean late = now.isAfter(deadline(startedAt, current.getQuestion()));

        // The server decides the result; an answer that arrives after the deadline counts as TIMEOUT (master 5.5).
        AnswerResult result = late ? AnswerResult.TIMEOUT : selected.isCorrect() ? AnswerResult.CORRECT : AnswerResult.WRONG;
        return recordResult(session, current, result, late ? null : selected, startedAt, now);
    }

    /**
     * Called by the client when its countdown reaches 0. Recording a timeout earlier than the server deadline
     * gives the child no advantage (score unchanged, streak reset), so no early-call rejection is needed.
     */
    @Override
    @Transactional
    public AnswerResponse submitTimeout(UUID anonymousId, UUID sessionId, SubmitTimeoutRequest request) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        requireStarted(session);
        SessionQuestion current = requireCurrentUnanswered(session, request.questionId());

        Instant now = clock.instant();
        return recordResult(session, current, AnswerResult.TIMEOUT, null, questionStartedAt(session, current), now);
    }

    // ------------------------------------------------------------------ finish

    @Override
    @Transactional
    public GameResultResponse finishSession(UUID anonymousId, UUID sessionId) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        switch (session.getStatus()) {
            case COMPLETED -> {
                return buildResult(session);
            }
            case ABANDONED -> throw new SessionNotActiveException();
            default -> {
                // STARTED: only completable when every question already has a result.
                boolean allAnswered = session.getSessionQuestions().stream().allMatch(sq -> sq.getResult() != null);
                if (!allAnswered) {
                    throw new BusinessException(ErrorCode.SESSION_NOT_ACTIVE, "Session still has unanswered questions");
                }
                Instant now = clock.instant();
                complete(session, now);
                playerRepository.addStats(session.getPlayer().getId(), 1, 0, 0, now);
                return buildResult(session);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GameResultResponse getResult(UUID anonymousId, UUID sessionId) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, false);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SESSION_NOT_ACTIVE, "Session is not completed");
        }
        return buildResult(session);
    }

    // --------------------------------------------------------------- abandoned

    @Override
    @Transactional
    public int abandonInactiveSessions() {
        Instant now = clock.instant();
        return sessionRepository.abandonInactive(now.minus(GameConstants.SESSION_INACTIVITY_TIMEOUT), now);
    }

    // ----------------------------------------------------------------- helpers

    /** Unknown player, unknown session and someone else's session are indistinguishable to the caller. */
    private GameSession loadOwnedSession(UUID anonymousId, UUID sessionId, boolean forUpdate) {
        AnonymousPlayer player = playerService.find(anonymousId).orElseThrow(SessionNotFoundException::new);
        Optional<GameSession> found = forUpdate
                ? sessionRepository.findBySessionIdForUpdate(sessionId)
                : sessionRepository.findBySessionId(sessionId);
        GameSession session = found.orElseThrow(SessionNotFoundException::new);
        if (!session.getPlayer().getId().equals(player.getId())) {
            throw new SessionNotFoundException();
        }
        return session;
    }

    private static void requireStarted(GameSession session) {
        switch (session.getStatus()) {
            case STARTED -> { }
            case COMPLETED -> throw new SessionAlreadyCompletedException();
            default -> throw new SessionNotActiveException();
        }
    }

    /** The question must belong to the session, be unanswered and be the current one (BR-011, BR-013). */
    private static SessionQuestion requireCurrentUnanswered(GameSession session, UUID questionId) {
        SessionQuestion sessionQuestion = session.getSessionQuestions().stream()
                .filter(sq -> sq.getQuestion().getId().equals(questionId))
                .findFirst()
                .orElseThrow(QuestionNotFoundException::new);
        if (sessionQuestion.getResult() != null) {
            throw sessionQuestion.getResult() == AnswerResult.TIMEOUT
                    ? new AnswerTimeoutException()
                    : new QuestionAlreadyAnsweredException();
        }
        if (sessionQuestion.getQuestionNumber().intValue() != session.getCurrentQuestionNumber().intValue()) {
            throw new QuestionNotFoundException("Question is not the current question of this session");
        }
        return sessionQuestion;
    }

    /** INVALID_OPTION when the id does not exist, OPTION_NOT_BELONG_TO_QUESTION when it is another question's option. */
    private QuestionOption resolveOption(GameQuestion question, UUID optionId) {
        return question.getOptions().stream()
                .filter(option -> option.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> optionRepository.existsById(optionId)
                        ? new OptionNotBelongToQuestionException()
                        : new InvalidOptionException());
    }

    private Optional<SessionQuestion> currentSessionQuestion(GameSession session) {
        return session.getSessionQuestions().stream()
                .filter(sq -> sq.getQuestionNumber().intValue() == session.getCurrentQuestionNumber().intValue())
                .findFirst();
    }

    /**
     * When the child started seeing the question, derived from server data only: question 1 starts with the
     * session; later questions start once the previous feedback has finished (answered_at + feedback delay).
     */
    private static Instant questionStartedAt(GameSession session, SessionQuestion current) {
        int number = current.getQuestionNumber().intValue();
        if (number <= 1) {
            return session.getStartedAt();
        }
        return session.getSessionQuestions().stream()
                .filter(sq -> sq.getQuestionNumber().intValue() == number - 1)
                .map(SessionQuestion::getAnsweredAt)
                .filter(answeredAt -> answeredAt != null)
                .findFirst()
                .map(answeredAt -> answeredAt.plusMillis(GameConstants.FEEDBACK_DELAY_MS))
                .orElse(session.getStartedAt());
    }

    private static Instant deadline(Instant startedAt, GameQuestion question) {
        return startedAt
                .plusSeconds(question.getTimeLimitSeconds())
                .plusMillis(GameConstants.ANSWER_GRACE_MS);
    }

    private AnswerResponse recordResult(
            GameSession session,
            SessionQuestion current,
            AnswerResult result,
            QuestionOption selected,
            Instant startedAt,
            Instant now) {
        int responseTimeMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, Duration.between(startedAt, now).toMillis()));
        current.setResult(result);
        current.setSelectedOption(selected);
        current.setAnsweredAt(now);
        current.setResponseTimeMs(responseTimeMs);

        applyScoring(session, result);

        boolean hasNextQuestion = session.getCurrentQuestionNumber() < session.getTotalQuestions();
        AnonymousPlayer player = session.getPlayer();
        if (hasNextQuestion) {
            session.setCurrentQuestionNumber((short) (session.getCurrentQuestionNumber() + 1));
        } else {
            complete(session, now);
        }

        playerRepository.addStats(
                player.getId(), hasNextQuestion ? 0 : 1, 1, result == AnswerResult.CORRECT ? 1 : 0, now);

        activityLogService.record(
                activityEventFor(result), player, session, session.getTopic(), current.getQuestion(), selected, responseTimeMs);

        QuestionOption correctOption = current.getQuestion().getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question " + current.getQuestion().getId() + " has no correct option"));

        // currentQuestionNumber has already advanced, so this is the question the child sees next.
        NextQuestionResponse nextQuestion = hasNextQuestion
                ? currentSessionQuestion(session).map(mapper::toNextQuestionResponse).orElse(null)
                : null;

        return new AnswerResponse(
                result,
                result == AnswerResult.CORRECT,
                session.getScore(),
                session.getCurrentStreak(),
                mapper.toCorrectAnswerResponse(correctOption),
                new FeedbackResponse(null, feedbackMessage(result)),
                hasNextQuestion,
                nextQuestion);
    }

    /** Master 5.7: correct +1 score and streak; wrong/timeout keep score and reset streak; score = correct answers. */
    private static void applyScoring(GameSession session, AnswerResult result) {
        switch (result) {
            case CORRECT -> {
                session.setCorrectAnswers((short) (session.getCorrectAnswers() + 1));
                session.setScore(session.getCorrectAnswers());
                session.setCurrentStreak((short) (session.getCurrentStreak() + 1));
                session.setMaxStreak((short) Math.max(session.getMaxStreak(), session.getCurrentStreak()));
            }
            case WRONG -> {
                session.setWrongAnswers((short) (session.getWrongAnswers() + 1));
                session.setCurrentStreak((short) 0);
            }
            case TIMEOUT -> {
                session.setTimeoutAnswers((short) (session.getTimeoutAnswers() + 1));
                session.setCurrentStreak((short) 0);
            }
        }
    }

    /** COMPLETED + finished_at (server time), FINISH_GAME log and sticker awards. No more answers are accepted. */
    private void complete(GameSession session, Instant now) {
        session.setStatus(SessionStatus.COMPLETED);
        session.setFinishedAt(now);
        AnonymousPlayer player = session.getPlayer();
        activityLogService.record(ActivityEventType.FINISH_GAME, player, session, session.getTopic(), null, null, null);
        stickerService.awardCompletionStickers(session, player, now);
    }

    private GameResultResponse buildResult(GameSession session) {
        int total = session.getTotalQuestions();
        double accuracy = Math.round(session.getCorrectAnswers() * 10000.0 / total) / 100.0;
        List<StickerResponse> earned = playerStickerRepository.findBySessionId(session.getId()).stream()
                .map(owned -> mapper.toStickerResponse(owned.getSticker(), owned.getEarnedAt()))
                .toList();
        return new GameResultResponse(
                session.getSessionId(),
                session.getTopic().getName(),
                total,
                session.getCorrectAnswers(),
                session.getWrongAnswers(),
                session.getTimeoutAnswers(),
                session.getScore(),
                accuracy,
                session.getCurrentStreak(),
                session.getMaxStreak(),
                earned);
    }

    private static ActivityEventType activityEventFor(AnswerResult result) {
        return switch (result) {
            case CORRECT -> ActivityEventType.ANSWER_CORRECT;
            case WRONG -> ActivityEventType.ANSWER_WRONG;
            case TIMEOUT -> ActivityEventType.ANSWER_TIMEOUT;
        };
    }

    private static String feedbackMessage(AnswerResult result) {
        return switch (result) {
            case CORRECT -> GameConstants.FEEDBACK_CORRECT;
            case WRONG -> GameConstants.FEEDBACK_WRONG;
            case TIMEOUT -> GameConstants.FEEDBACK_TIMEOUT;
        };
    }
}
