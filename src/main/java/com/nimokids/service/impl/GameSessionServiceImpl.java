package com.nimokids.service.impl;

import com.nimokids.service.language.ResolvedQuestion;
import com.nimokids.service.language.QuestionInstance;
import com.nimokids.service.language.I18nContent;
import com.nimokids.service.LanguageResolverService;
import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.request.TimerStartRequest;
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
import com.nimokids.entity.QuestionSnapshot;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.AgeGroup;
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
import com.nimokids.repository.TopicRepository;
import com.nimokids.service.ActivityLogService;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.OptionGenerator;
import com.nimokids.service.PlayerService;
import com.nimokids.service.StickerService;
import com.nimokids.util.GameConstants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionServiceImpl implements GameSessionService {

    private final GameSessionRepository sessionRepository;
    private final GameQuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final GameModeRepository gameModeRepository;
    private final AnonymousPlayerRepository playerRepository;
    private final PlayerStickerRepository playerStickerRepository;
    private final PlayerService playerService;
    private final StickerService stickerService;
    private final ActivityLogService activityLogService;
    private final OptionGenerator optionGenerator;
    private final LanguageResolverService languageResolver;
    private final GameMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ create

    /**
     * One atomic transaction (master 5.1): pick 5 distinct valid questions from the topic SUBTREE, generate and snapshot
     * the 4 options of each, create the session and its 5 session_questions. Options are generated now, once, so a
     * refreshed page gets the same options in the same order.
     */
    @Override
    @Transactional
    public GameSessionResponse createSession(UUID anonymousId, CreateGameSessionRequest request) {
        Instant now = clock.instant();
        AnonymousPlayer player = playerService.resolveOrCreate(anonymousId);

        // MIX mode: topicId is null — draw from all active root topics.
        Topic topic;
        List<UUID> subtree;
        if (request.topicId() != null) {
            topic = topicRepository.findById(request.topicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", request.topicId()));
            if (!topic.isActive()) {
                throw new TopicNotPlayableException();
            }
            subtree = topicRepository.findActiveSubtreeIds(topic.getId());
            if (subtree.isEmpty()) {
                throw new TopicNotPlayableException();
            }
        } else {
            topic = null;
            subtree = topicRepository.findAllActiveSubtreeIds();
            if (subtree.isEmpty()) {
                throw new TopicNotPlayableException();
            }
        }

        GameMode mode = gameModeRepository.findById(request.gameModeId())
                .orElseThrow(() -> new ResourceNotFoundException("Game mode", request.gameModeId()));
        if (!mode.isActive()) {
            throw new GameModeNotPlayableException();
        }

        AgeGroup ageGroup = request.resolvedAgeGroup();
        LanguageMode languageMode = request.resolvedLanguageMode();
        String[] ageGroups = ageGroup.inheritedGroupNames().toArray(String[]::new);
        List<UUID> candidateIds;
        if (ageGroup == AgeGroup.AGE_4_5) {
            List<UUID> hard = new ArrayList<>(questionRepository.findCandidateIds(subtree, mode.getId(), new String[]{AgeGroup.AGE_4_5.name()}));
            List<UUID> easy = new ArrayList<>(questionRepository.findCandidateIds(subtree, mode.getId(), new String[]{AgeGroup.AGE_1_3.name()}));
            if (hard.size() + easy.size() < GameConstants.QUESTIONS_PER_SESSION) {
                throw new InsufficientQuestionsException();
            }
            candidateIds = mixByAgeGroup(hard, easy);
        } else {
            candidateIds = new ArrayList<>(questionRepository.findCandidateIds(subtree, mode.getId(), ageGroups));
            if (candidateIds.size() < GameConstants.QUESTIONS_PER_SESSION) {
                throw new InsufficientQuestionsException();
            }
            Collections.shuffle(candidateIds, ThreadLocalRandom.current());
        }

        GameSession session = GameSession.builder()
                .player(player)
                .topic(topic)
                .gameMode(mode)
                .languageMode(languageMode)
                .totalQuestions((short) GameConstants.QUESTIONS_PER_SESSION)
                .startedAt(now)
                .build();

        // The first candidates are loaded in ONE query with everything the snapshot needs (a few spare ones in case some are skipped).
        Map<UUID, GameQuestion> preloaded = new HashMap<>();
        questionRepository.findAllWithDetails(candidateIds.subList(0, Math.min(candidateIds.size(), GameConstants.QUESTIONS_PER_SESSION + 3)))
                .forEach(loaded -> preloaded.put(loaded.getId(), loaded));

        int accepted = 0;
        for (UUID candidateId : candidateIds) {
            if (accepted == GameConstants.QUESTIONS_PER_SESSION) {
                break;
            }
            GameQuestion question = preloaded.containsKey(candidateId) ? preloaded.get(candidateId) : questionRepository.findById(candidateId).orElse(null);
            if (question == null) {
                continue;
            }
            ResolvedQuestion resolved;
            try {
                // 1) generate (language-free)  2) resolve the language  3) save: the resolver is the last step.
                List<SnapshotOption> generated = optionGenerator.generate(question);
                resolved = languageResolver.resolve(languageMode, new QuestionInstance(question, generated));
            } catch (BusinessException ex) {
                log.warn("Skipping question {}: {}", candidateId, ex.getMessage());
                continue;
            }
            accepted++;
            session.addSessionQuestion(SessionQuestion.builder()
                    .question(question)
                    .questionNumber((short) accepted)
                    .optionsSnapshot(resolved.options())
                    .questionSnapshot(QuestionSnapshot.of(question, resolved))
                    .presentedAt(accepted == 1 ? now : null)
                    .build());
        }
        if (accepted < GameConstants.QUESTIONS_PER_SESSION) {
            throw new InsufficientQuestionsException();
        }
        sessionRepository.save(session);

        activityLogService.record(ActivityEventType.START_GAME, player, session, topic, null, null, null);
        return mapper.toSessionResponse(session, currentSessionQuestion(session).orElse(null));
    }

    // -------------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public GameSessionResponse getSession(UUID anonymousId, UUID sessionId) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, false);
        SessionQuestion current = session.getStatus() == SessionStatus.STARTED
                ? currentSessionQuestion(session).orElse(null)
                : null;
        return mapper.toSessionResponse(session, current);
    }

    // ------------------------------------------------------------------- timer

    @Override
    @Transactional
    public void startTimer(UUID anonymousId, UUID sessionId, TimerStartRequest request) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        requireStarted(session);
        SessionQuestion current = requireCurrentUnanswered(session, request.questionId());
        if (Boolean.TRUE.equals(request.restart())) {
            // At most MAX_LISTEN_AGAIN restarts per question; further calls are ignored (best-effort from the client).
            if (current.getTimerRestarts() < GameConstants.MAX_LISTEN_AGAIN) {
                current.setTimerRestarts((short) (current.getTimerRestarts() + 1));
                current.setTimerRestartedAt(clock.instant());
            }
        } else if (current.getTimerStartedAt() == null) {
            current.setTimerStartedAt(clock.instant());
        }
    }

    // ------------------------------------------------------------------ answer

    /**
     * Master 5.4: lock and validate the session, validate player/state/question/option/timeout, grade against the
     * options SNAPSHOT, update session_question, game_session and player statistics, award stickers and write the
     * activity log, all in one transaction. The row lock on the session serializes concurrent requests.
     */
    @Override
    @Transactional
    public AnswerResponse submitAnswer(UUID anonymousId, UUID sessionId, SubmitAnswerRequest request) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        requireStarted(session);
        SessionQuestion current = requireCurrentUnanswered(session, request.questionId());
        SnapshotOption selected = resolveOption(session, current, request.selectedOptionId());

        Instant now = clock.instant();
        Instant startedAt = effectiveTimerStart(session, current);
        boolean late = now.isAfter(deadline(startedAt, current.getQuestion()));

        // The server decides: the snapshot says whether the option is right; an answer after the deadline is a TIMEOUT.
        AnswerResult result = late ? AnswerResult.TIMEOUT : selected.isCorrect() ? AnswerResult.CORRECT : AnswerResult.WRONG;
        return recordResult(session, current, result, late ? null : selected, startedAt, now);
    }

    /**
     * Called by the client when its countdown reaches 0. Recording a timeout earlier than the server deadline gives the
     * child no advantage (score unchanged, streak reset), so no early-call rejection is needed.
     */
    @Override
    @Transactional
    public AnswerResponse submitTimeout(UUID anonymousId, UUID sessionId, SubmitTimeoutRequest request) {
        GameSession session = loadOwnedSession(anonymousId, sessionId, true);
        requireStarted(session);
        SessionQuestion current = requireCurrentUnanswered(session, request.questionId());

        Instant now = clock.instant();
        return recordResult(session, current, AnswerResult.TIMEOUT, null, effectiveTimerStart(session, current), now);
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

    /**
     * AGE_4_5: orders the candidates so the first questions follow {@link GameConstants#AGE_4_5_MIX_PATTERN}
     * (60 % AGE_4_5, 40 % AGE_1_3). Each pool is shuffled; a pool that runs out is replaced by the other one.
     */
    static List<UUID> mixByAgeGroup(List<UUID> hard, List<UUID> easy) {
        Collections.shuffle(hard, ThreadLocalRandom.current());
        Collections.shuffle(easy, ThreadLocalRandom.current());
        String pattern = GameConstants.AGE_4_5_MIX_PATTERN;
        List<UUID> mixed = new ArrayList<>(hard.size() + easy.size());
        int h = 0;
        int e = 0;
        for (int slot = 0; h < hard.size() || e < easy.size(); slot++) {
            boolean wantHard = pattern.charAt(slot % pattern.length()) == 'H';
            if (wantHard ? h < hard.size() : e >= easy.size()) {
                mixed.add(hard.get(h++));
            } else {
                mixed.add(easy.get(e++));
            }
        }
        return mixed;
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

    /**
     * The submitted option id is looked up in THIS question's snapshot. INVALID_OPTION when it is unknown, and
     * OPTION_NOT_BELONG_TO_QUESTION when it belongs to another question of the same session.
     */
    private static SnapshotOption resolveOption(GameSession session, SessionQuestion current, UUID optionId) {
        return current.getOptionsSnapshot().stream()
                .filter(option -> option.optionId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> session.getSessionQuestions().stream()
                        .filter(other -> other != current)
                        .anyMatch(other -> other.getOptionsSnapshot().stream().anyMatch(option -> option.optionId().equals(optionId)))
                        ? new OptionNotBelongToQuestionException()
                        : new InvalidOptionException());
    }

    private static Optional<SessionQuestion> currentSessionQuestion(GameSession session) {
        return session.getSessionQuestions().stream()
                .filter(sq -> sq.getQuestionNumber().intValue() == session.getCurrentQuestionNumber().intValue())
                .findFirst();
    }

    /**
     * When the child's countdown really started (master 6.3). The client starts it when the question audio ends and
     * reports that, but the server never trusts the report alone: the start cannot be later than
     * "question presented + (feedback pause for questions after the first) + audio length + tolerance".
     * Without a report, that latest allowed moment is used. So a client cannot gain time by reporting late or never.
     */
    static Instant effectiveTimerStart(GameSession session, SessionQuestion sessionQuestion) {
        if (sessionQuestion.getTimerRestartedAt() != null) {
            return sessionQuestion.getTimerRestartedAt(); // "Listen again": the server's own clock at that call
        }
        Instant presentedAt = presentedAt(session, sessionQuestion);
        long audioMs = 0;
        var voice = sessionQuestion.getQuestion().getQuestionVoice();
        if (voice != null && voice.getDurationMs() != null) {
            audioMs = voice.getDurationMs();
        }
        long feedbackMs = sessionQuestion.getQuestionNumber() > 1 ? GameConstants.FEEDBACK_ALLOWANCE_MS : 0;
        Instant latestStart = presentedAt.plusMillis(feedbackMs + audioMs + GameConstants.TIMER_START_TOLERANCE_MS);

        Instant reported = sessionQuestion.getTimerStartedAt();
        if (reported == null) {
            return latestStart;
        }
        return reported.isBefore(latestStart) ? reported : latestStart;
    }

    private static Instant presentedAt(GameSession session, SessionQuestion sessionQuestion) {
        if (sessionQuestion.getPresentedAt() != null) {
            return sessionQuestion.getPresentedAt();
        }
        // Rows created before presented_at existed: fall back to the previous answer, or the session start.
        int number = sessionQuestion.getQuestionNumber();
        if (number > 1) {
            return session.getSessionQuestions().stream()
                    .filter(sq -> sq.getQuestionNumber().intValue() == number - 1)
                    .map(SessionQuestion::getAnsweredAt)
                    .filter(answeredAt -> answeredAt != null)
                    .findFirst()
                    .orElse(session.getStartedAt());
        }
        return session.getStartedAt();
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
            SnapshotOption selected,
            Instant startedAt,
            Instant now) {
        int responseTimeMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, Duration.between(startedAt, now).toMillis()));
        current.setResult(result);
        current.setSelectedOptionId(selected == null ? null : selected.optionId());
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
                activityEventFor(result), player, session, session.getTopic(), current.getQuestion(),
                selected == null ? null : selected.optionId(), responseTimeMs);

        // The correct option is revealed only now, after the answer has been recorded.
        SnapshotOption correctOption = current.getOptionsSnapshot().stream()
                .filter(SnapshotOption::isCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Snapshot of question " + current.getQuestion().getId() + " has no correct option"));

        // currentQuestionNumber has already advanced, so this is the question the child sees next.
        NextQuestionResponse nextQuestion = null;
        if (hasNextQuestion) {
            SessionQuestion next = currentSessionQuestion(session).orElse(null);
            if (next != null) {
                next.setPresentedAt(now);
                nextQuestion = mapper.toNextQuestionResponse(next);
            }
        }

        return new AnswerResponse(
                result,
                result == AnswerResult.CORRECT,
                session.getScore(),
                session.getCurrentStreak(),
                mapper.toCorrectAnswerResponse(correctOption),
                new FeedbackResponse(null, feedbackMessage(result, session.getLanguageMode())),
                hasNextQuestion,
                nextQuestion);
    }

    /** Master 5.5: correct +1 score and streak; wrong/timeout keep score and reset streak; score = correct answers. */
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
        LanguageMode language = session.getLanguageMode();
        String topicName = session.getTopic() != null ? session.getTopic().nameFor(language)
                : (language.servesVietnamese() ? GameConstants.ALL_TOPICS_VI : GameConstants.ALL_TOPICS_EN);
        return new GameResultResponse(
                session.getSessionId(),
                topicName,
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

    private static String feedbackMessage(AnswerResult result, LanguageMode language) {
        boolean vi = I18nContent.VI.equals(language.instructionLanguage());
        return switch (result) {
            case CORRECT -> vi ? GameConstants.FEEDBACK_CORRECT_VI : GameConstants.FEEDBACK_CORRECT;
            case WRONG -> vi ? GameConstants.FEEDBACK_WRONG_VI : GameConstants.FEEDBACK_WRONG;
            case TIMEOUT -> vi ? GameConstants.FEEDBACK_TIMEOUT_VI : GameConstants.FEEDBACK_TIMEOUT;
        };
    }
}
