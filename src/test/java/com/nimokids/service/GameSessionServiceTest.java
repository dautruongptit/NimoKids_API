package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
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
import com.nimokids.exception.InsufficientQuestionsException;
import com.nimokids.exception.InvalidOptionException;
import com.nimokids.exception.OptionNotBelongToQuestionException;
import com.nimokids.exception.QuestionAlreadyAnsweredException;
import com.nimokids.exception.QuestionNotFoundException;
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
import com.nimokids.service.impl.GameSessionServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameSessionServiceTest {

    private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");

    private final GameSessionRepository sessionRepository = mock(GameSessionRepository.class);
    private final GameQuestionRepository questionRepository = mock(GameQuestionRepository.class);
    private final QuestionOptionRepository optionRepository = mock(QuestionOptionRepository.class);
    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final GameModeRepository gameModeRepository = mock(GameModeRepository.class);
    private final AnonymousPlayerRepository playerRepository = mock(AnonymousPlayerRepository.class);
    private final PlayerStickerRepository playerStickerRepository = mock(PlayerStickerRepository.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final StickerService stickerService = mock(StickerService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final MutableClock clock = new MutableClock(START);

    private GameSessionServiceImpl service;

    private UUID anonymousId;
    private AnonymousPlayer player;
    private Topic topic;
    private GameMode mode;
    private GameSession session;

    @BeforeEach
    void setUp() {
        service = new GameSessionServiceImpl(
                sessionRepository, questionRepository, optionRepository, topicRepository, gameModeRepository,
                playerRepository, playerStickerRepository, playerService, stickerService, activityLogService,
                new GameMapper(), clock);

        anonymousId = UUID.randomUUID();
        player = player(anonymousId);
        topic = Topic.builder().code("ANIMALS").name("Animals").slug("animals")
                .minAge((short) 1).maxAge((short) 5).build();
        topic.setId(UUID.randomUUID());
        mode = GameMode.builder().code("GUESS").name("Guess").build();
        mode.setId(UUID.randomUUID());

        session = startedSession(player);
        when(playerService.find(anonymousId)).thenReturn(Optional.of(player));
        when(sessionRepository.findBySessionIdForUpdate(session.getSessionId())).thenReturn(Optional.of(session));
        when(sessionRepository.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        when(playerStickerRepository.findBySessionId(any())).thenReturn(List.of());
    }

    // ---------------------------------------------------------------- scoring

    @Test
    void correctAnswerAddsScoreAndStreakAndMovesToNextQuestion() {
        clock.advance(Duration.ofSeconds(2));

        AnswerResponse response = answer(1, 0);

        assertThat(response.result()).isEqualTo(AnswerResult.CORRECT);
        assertThat(response.correct()).isTrue();
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.currentStreak()).isEqualTo(1);
        assertThat(response.hasNextQuestion()).isTrue();
        // The next question travels with the answer, so the frontend needs no extra call.
        assertThat(response.nextQuestion()).isNotNull();
        assertThat(response.nextQuestion().questionNumber()).isEqualTo(2);
        assertThat(response.nextQuestion().timeLimitSeconds()).isEqualTo(5);
        assertThat(response.nextQuestion().question().id()).isEqualTo(question(2).getId());
        assertThat(response.nextQuestion().question().options()).hasSize(4);
        assertThat(response.correctAnswer().id()).isEqualTo(question(1).getOptions().get(0).getId());
        assertThat(session.getCurrentQuestionNumber()).isEqualTo((short) 2);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STARTED);

        SessionQuestion first = session.getSessionQuestions().get(0);
        assertThat(first.getResult()).isEqualTo(AnswerResult.CORRECT);
        assertThat(first.getSelectedOption()).isSameAs(question(1).getOptions().get(0));
        assertThat(first.getResponseTimeMs()).isEqualTo(2000);
        verify(playerRepository).addStats(player.getId(), 0, 1, 1, clock.instant());
        verify(activityLogService).record(eq(ActivityEventType.ANSWER_CORRECT), eq(player), eq(session), eq(topic),
                eq(question(1)), eq(question(1).getOptions().get(0)), eq(2000));
    }

    @Test
    void wrongAnswerKeepsScoreAndResetsStreakButKeepsMaxStreak() {
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);
        clock.advance(Duration.ofSeconds(2));
        answer(2, 0);
        clock.advance(Duration.ofSeconds(2));

        AnswerResponse response = answer(3, 1);

        assertThat(response.result()).isEqualTo(AnswerResult.WRONG);
        assertThat(response.correct()).isFalse();
        assertThat(response.score()).isEqualTo(2);
        assertThat(response.currentStreak()).isZero();
        assertThat(session.getWrongAnswers()).isEqualTo((short) 1);
        assertThat(session.getMaxStreak()).isEqualTo((short) 2);
        assertThat(response.correctAnswer().id()).isEqualTo(question(3).getOptions().get(0).getId());
    }

    @Test
    void timeoutEndpointKeepsScoreAndResetsStreak() {
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);
        clock.advance(Duration.ofSeconds(6));

        AnswerResponse response = service.submitTimeout(anonymousId, session.getSessionId(),
                new SubmitTimeoutRequest(question(2).getId()));

        assertThat(response.result()).isEqualTo(AnswerResult.TIMEOUT);
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.nextQuestion().question().id()).isEqualTo(question(3).getId());
        assertThat(session.getTimeoutAnswers()).isEqualTo((short) 1);
        assertThat(session.getSessionQuestions().get(1).getSelectedOption()).isNull();
    }

    @Test
    void answerArrivingAfterServerDeadlineIsRecordedAsTimeoutEvenIfCorrect() {
        clock.advance(Duration.ofSeconds(10));

        AnswerResponse response = answer(1, 0);

        assertThat(response.result()).isEqualTo(AnswerResult.TIMEOUT);
        assertThat(response.correct()).isFalse();
        assertThat(response.score()).isZero();
        assertThat(session.getSessionQuestions().get(0).getSelectedOption()).isNull();
        verify(playerRepository).addStats(player.getId(), 0, 1, 0, clock.instant());
    }

    @Test
    void deadlineOfLaterQuestionsStartsAfterPreviousFeedback() {
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);
        // Question 2 started at t+1.8s, so t+7s is still inside the 5s limit + 1s grace (deadline t+7.8s).
        clock.advance(Duration.ofSeconds(6));

        AnswerResponse response = answer(2, 0);

        assertThat(response.result()).isEqualTo(AnswerResult.CORRECT);
    }

    // ------------------------------------------------------------- validation

    @Test
    void duplicateAnswerIsRejectedAndScoreIsNotIncrementedTwice() {
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);

        assertThatThrownBy(() -> answer(1, 0)).isInstanceOf(QuestionAlreadyAnsweredException.class);

        assertThat(session.getScore()).isEqualTo((short) 1);
        verify(playerRepository, times(1)).addStats(any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void answeringATimedOutQuestionIsRejectedWithAnswerTimeout() {
        clock.advance(Duration.ofSeconds(6));
        service.submitTimeout(anonymousId, session.getSessionId(), new SubmitTimeoutRequest(question(1).getId()));

        assertThatThrownBy(() -> answer(1, 0)).isInstanceOf(AnswerTimeoutException.class);
    }

    @Test
    void optionOfAnotherQuestionIsRejected() {
        UUID foreignOption = question(2).getOptions().get(0).getId();
        when(optionRepository.existsById(foreignOption)).thenReturn(true);

        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), foreignOption)))
                .isInstanceOf(OptionNotBelongToQuestionException.class);
        assertThat(session.getSessionQuestions().get(0).getResult()).isNull();
    }

    @Test
    void unknownOptionIsRejected() {
        UUID unknown = UUID.randomUUID();
        when(optionRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), unknown)))
                .isInstanceOf(InvalidOptionException.class);
    }

    @Test
    void onlyTheCurrentQuestionCanBeAnswered() {
        assertThatThrownBy(() -> answer(3, 0))
                .isInstanceOf(QuestionNotFoundException.class)
                .hasMessageContaining("current question");
    }

    @Test
    void questionOutsideTheSessionIsRejected() {
        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void sessionOfAnotherPlayerLooksLikeItDoesNotExist() {
        UUID otherAnonymousId = UUID.randomUUID();
        when(playerService.find(otherAnonymousId)).thenReturn(Optional.of(player(otherAnonymousId)));

        assertThatThrownBy(() -> service.submitAnswer(otherAnonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), question(1).getOptions().get(0).getId())))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.getSession(otherAnonymousId, session.getSessionId()))
                .isInstanceOf(SessionNotFoundException.class);
        assertThat(session.getScore()).isZero();
    }

    @Test
    void unknownPlayerOrSessionIsSessionNotFound() {
        UUID unknownAnonymousId = UUID.randomUUID();
        when(playerService.find(unknownAnonymousId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(unknownAnonymousId, session.getSessionId()))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.getSession(anonymousId, UUID.randomUUID()))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void abandonedSessionCannotBeAnswered() {
        session.setStatus(SessionStatus.ABANDONED);

        assertThatThrownBy(() -> answer(1, 0)).isInstanceOf(SessionNotActiveException.class);
    }

    // -------------------------------------------------------------- completion

    @Test
    void fifthAnswerCompletesTheSessionAndRejectsFurtherAnswers() {
        for (int number = 1; number <= 5; number++) {
            clock.advance(Duration.ofSeconds(1));
            AnswerResponse response = answer(number, 0);
            assertThat(response.hasNextQuestion()).isEqualTo(number < 5);
            if (number < 5) {
                assertThat(response.nextQuestion().question().id()).isEqualTo(question(number + 1).getId());
            } else {
                assertThat(response.nextQuestion()).isNull();
            }
        }

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getFinishedAt()).isEqualTo(clock.instant());
        assertThat(session.getScore()).isEqualTo((short) 5);
        assertThat(session.getMaxStreak()).isEqualTo((short) 5);
        verify(playerRepository).addStats(player.getId(), 1, 1, 1, clock.instant());
        verify(stickerService, times(1)).awardCompletionStickers(session, player, clock.instant());
        verify(activityLogService).record(eq(ActivityEventType.FINISH_GAME), eq(player), eq(session), eq(topic),
                isNull(), isNull(), isNull());

        assertThatThrownBy(() -> answer(5, 0)).isInstanceOf(SessionAlreadyCompletedException.class);
        verify(stickerService, times(1)).awardCompletionStickers(any(), any(), any());
    }

    @Test
    void resultReportsAccuracyAndCountersAfterCompletion() {
        int[] optionIndexes = {0, 1, 0, 0, 0};
        for (int number = 1; number <= 5; number++) {
            clock.advance(Duration.ofSeconds(1));
            answer(number, optionIndexes[number - 1]);
        }

        GameResultResponse result = service.getResult(anonymousId, session.getSessionId());

        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.correctAnswers()).isEqualTo(4);
        assertThat(result.wrongAnswers()).isEqualTo(1);
        assertThat(result.timeoutAnswers()).isZero();
        assertThat(result.score()).isEqualTo(4);
        assertThat(result.accuracy()).isEqualTo(80.0);
        assertThat(result.maxStreak()).isEqualTo(3);
        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.topic()).isEqualTo("Animals");
    }

    @Test
    void resultIsNotAvailableUntilTheSessionIsCompleted() {
        assertThatThrownBy(() -> service.getResult(anonymousId, session.getSessionId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_ACTIVE));
    }

    @Test
    void getSessionShowsCurrentQuestionWithoutRevealingTheCorrectOption() {
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);

        GameSessionResponse response = service.getSession(anonymousId, session.getSessionId());

        assertThat(response.currentQuestionNumber()).isEqualTo(2);
        assertThat(response.question().id()).isEqualTo(question(2).getId());
        assertThat(response.question().options()).hasSize(4);
        assertThat(response.timeLimitSeconds()).isEqualTo(5);
    }

    // ----------------------------------------------------------------- create

    @Test
    void createSessionSelectsFiveUniqueQuestionsNumberedOneToFive() {
        List<GameQuestion> pool = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pool.add(newQuestion());
        }
        stubCreateDependencies(pool);

        GameSessionResponse response = service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null));

        ArgumentCaptor<GameSession> saved = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionRepository).save(saved.capture());
        GameSession created = saved.getValue();
        assertThat(created.getSessionQuestions()).hasSize(5);
        assertThat(created.getSessionQuestions().stream().map(sq -> sq.getQuestionNumber().intValue()).toList())
                .containsExactly(1, 2, 3, 4, 5);
        Set<UUID> questionIds = new HashSet<>();
        created.getSessionQuestions().forEach(sq -> questionIds.add(sq.getQuestion().getId()));
        assertThat(questionIds).hasSize(5);
        assertThat(created.getStatus()).isEqualTo(SessionStatus.STARTED);
        assertThat(created.getTotalQuestions()).isEqualTo((short) 5);
        assertThat(created.getStartedAt()).isEqualTo(START);
        assertThat(response.question().id()).isEqualTo(created.getSessionQuestions().get(0).getQuestion().getId());
        assertThat(response.currentQuestionNumber()).isEqualTo(1);
        verify(activityLogService).record(eq(ActivityEventType.START_GAME), eq(player), eq(created), eq(topic),
                isNull(), isNull(), isNull());
    }

    @Test
    void createSessionNeedsAtLeastFivePlayableQuestions() {
        List<GameQuestion> pool = List.of(newQuestion(), newQuestion(), newQuestion(), newQuestion());
        stubCreateDependencies(pool);

        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(InsufficientQuestionsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSessionRejectsInactiveTopic() {
        topic.setActive(false);
        stubCreateDependencies(List.of());

        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(TopicNotPlayableException.class);
    }

    // ------------------------------------------------------------- fixtures

    private void stubCreateDependencies(List<GameQuestion> pool) {
        when(playerService.resolveOrCreate(anonymousId)).thenReturn(player);
        when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
        when(gameModeRepository.findById(mode.getId())).thenReturn(Optional.of(mode));
        when(questionRepository.findPlayableIds(topic.getId(), mode.getId(), 0))
                .thenReturn(pool.stream().map(GameQuestion::getId).toList());
        when(questionRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            List<GameQuestion> found = new ArrayList<>();
            ids.forEach(id -> pool.stream().filter(q -> q.getId().equals(id)).findFirst().ifPresent(found::add));
            return found;
        });
    }

    private AnswerResponse answer(int questionNumber, int optionIndex) {
        GameQuestion question = question(questionNumber);
        return service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question.getId(), question.getOptions().get(optionIndex).getId()));
    }

    private GameQuestion question(int questionNumber) {
        return session.getSessionQuestions().get(questionNumber - 1).getQuestion();
    }

    private AnonymousPlayer player(UUID anonymousId) {
        AnonymousPlayer created = AnonymousPlayer.builder()
                .anonymousId(anonymousId).firstSeenAt(START).lastSeenAt(START).build();
        created.setId(UUID.randomUUID());
        return created;
    }

    private GameSession startedSession(AnonymousPlayer owner) {
        GameSession created = GameSession.builder()
                .player(owner).topic(topic).gameMode(mode).startedAt(START).build();
        created.setId(UUID.randomUUID());
        for (int number = 1; number <= 5; number++) {
            created.addSessionQuestion(SessionQuestion.builder()
                    .question(newQuestion()).questionNumber((short) number).build());
        }
        return created;
    }

    /** A question with 4 options: index 0 is correct, indexes 1-3 are wrong. */
    private GameQuestion newQuestion() {
        GameQuestion question = GameQuestion.builder()
                .topic(topic).gameMode(mode).questionText("Which animal says Meow?")
                .difficulty((short) 1).minAge((short) 1).maxAge((short) 5).build();
        question.setId(UUID.randomUUID());
        for (int i = 0; i < 4; i++) {
            QuestionOption option = QuestionOption.builder()
                    .optionText("Option " + i).correct(i == 0).displayOrder(i + 1).build();
            option.setId(UUID.randomUUID());
            question.addOption(option);
        }
        return question;
    }

    /** Test clock whose time only moves when the test says so. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
