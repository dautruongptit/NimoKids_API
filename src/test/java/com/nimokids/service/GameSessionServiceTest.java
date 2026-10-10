package com.nimokids.service;

import com.nimokids.service.language.ResolvedQuestion;
import com.nimokids.service.language.QuestionInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.request.SubmitTimeoutRequest;
import com.nimokids.dto.request.TimerStartRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.ActivityEventType;
import com.nimokids.entity.enums.AgeGroup;
import com.nimokids.entity.enums.AnswerResult;
import com.nimokids.entity.enums.SessionStatus;
import com.nimokids.exception.AnswerTimeoutException;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.InsufficientDistractorsException;
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
    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final GameModeRepository gameModeRepository = mock(GameModeRepository.class);
    private final AnonymousPlayerRepository playerRepository = mock(AnonymousPlayerRepository.class);
    private final PlayerStickerRepository playerStickerRepository = mock(PlayerStickerRepository.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final StickerService stickerService = mock(StickerService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final OptionGenerator optionGenerator = mock(OptionGenerator.class);
    private final LanguageResolverService languageResolver = mock(LanguageResolverService.class);
    private final MutableClock clock = new MutableClock(START);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private GameSessionServiceImpl service;

    private UUID anonymousId;
    private AnonymousPlayer player;
    private Topic topic;
    private GameMode mode;
    private GameSession session;

    @BeforeEach
    void setUp() {
        service = new GameSessionServiceImpl(
                sessionRepository, questionRepository, topicRepository, gameModeRepository, playerRepository,
                playerStickerRepository, playerService, stickerService, activityLogService, optionGenerator,
                languageResolver, new GameMapper(), clock);
        when(languageResolver.resolve(any(), any())).thenAnswer(invocation -> {
            QuestionInstance instance = invocation.getArgument(1);
            return new ResolvedQuestion(instance.question().getQuestionText(), null, instance.options(),
                    invocation.getArgument(0), "en", "en");
        });

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
    void correctAnswerIsGradedAgainstTheSnapshotAndAddsScoreAndStreak() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(2));

        AnswerResponse response = answer(1, 0);

        assertThat(response.result()).isEqualTo(AnswerResult.CORRECT);
        assertThat(response.correct()).isTrue();
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.currentStreak()).isEqualTo(1);
        assertThat(response.hasNextQuestion()).isTrue();
        assertThat(response.correctAnswer().id()).isEqualTo(option(1, 0).optionId());
        assertThat(session.getCurrentQuestionNumber()).isEqualTo((short) 2);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STARTED);

        SessionQuestion first = session.getSessionQuestions().get(0);
        assertThat(first.getResult()).isEqualTo(AnswerResult.CORRECT);
        assertThat(first.getSelectedOptionId()).isEqualTo(option(1, 0).optionId());
        assertThat(first.getResponseTimeMs()).isEqualTo(2000);
        verify(playerRepository).addStats(player.getId(), 0, 1, 1, clock.instant());
        verify(activityLogService).record(eq(ActivityEventType.ANSWER_CORRECT), eq(player), eq(session), eq(topic),
                eq(question(1)), eq(option(1, 0).optionId()), eq(2000));
    }

    @Test
    void theNextQuestionTravelsWithTheAnswerAndRevealsNoCorrectness() throws Exception {
        startTimer(1);
        clock.advance(Duration.ofSeconds(2));

        AnswerResponse response = answer(1, 1);

        assertThat(response.nextQuestion()).isNotNull();
        assertThat(response.nextQuestion().questionNumber()).isEqualTo(2);
        assertThat(response.nextQuestion().timeLimitSeconds()).isEqualTo(8);
        assertThat(response.nextQuestion().question().id()).isEqualTo(question(2).getId());
        assertThat(response.nextQuestion().question().options()).hasSize(4);
        assertThat(response.nextQuestion().question().options().get(0).id()).isEqualTo(option(2, 0).optionId());

        String nextQuestionJson = objectMapper.writeValueAsString(response.nextQuestion());
        assertThat(nextQuestionJson).doesNotContainIgnoringCase("correct");
    }

    @Test
    void sessionAndQuestionPayloadsNeverCarryACorrectnessFlag() throws Exception {
        String json = objectMapper.writeValueAsString(service.getSession(anonymousId, session.getSessionId()));

        assertThat(json).doesNotContainIgnoringCase("correct");
        assertThat(json).contains("\"options\"").contains(option(1, 0).optionId().toString());
    }

    @Test
    void wrongAnswerKeepsScoreAndResetsStreakButKeepsMaxStreak() {
        startTimer(1);
        answer(1, 0);
        startTimer(2);
        answer(2, 0);
        startTimer(3);

        AnswerResponse response = answer(3, 1);

        assertThat(response.result()).isEqualTo(AnswerResult.WRONG);
        assertThat(response.correct()).isFalse();
        assertThat(response.score()).isEqualTo(2);
        assertThat(response.currentStreak()).isZero();
        assertThat(session.getWrongAnswers()).isEqualTo((short) 1);
        assertThat(session.getMaxStreak()).isEqualTo((short) 2);
        assertThat(response.correctAnswer().id()).isEqualTo(option(3, 0).optionId());
    }

    @Test
    void timeoutEndpointKeepsScoreAndResetsStreak() {
        startTimer(1);
        answer(1, 0);
        startTimer(2);
        clock.advance(Duration.ofSeconds(9));

        AnswerResponse response = service.submitTimeout(anonymousId, session.getSessionId(),
                new SubmitTimeoutRequest(question(2).getId()));

        assertThat(response.result()).isEqualTo(AnswerResult.TIMEOUT);
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.nextQuestion().question().id()).isEqualTo(question(3).getId());
        assertThat(session.getTimeoutAnswers()).isEqualTo((short) 1);
        assertThat(session.getSessionQuestions().get(1).getSelectedOptionId()).isNull();
    }

    // ------------------------------------------------------------------- timer

    @Test
    void theDeadlineIsEightSecondsPlusOneSecondOfGraceAfterTheReportedStart() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(9));
        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.CORRECT);
    }

    @Test
    void anAnswerAfterTheDeadlineIsATimeoutEvenIfItWasTheRightOne() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(9).plusMillis(1));

        AnswerResponse response = answer(1, 0);

        assertThat(response.result()).isEqualTo(AnswerResult.TIMEOUT);
        assertThat(response.correct()).isFalse();
        assertThat(response.score()).isZero();
        assertThat(session.getSessionQuestions().get(0).getSelectedOptionId()).isNull();
        verify(playerRepository).addStats(player.getId(), 0, 1, 0, clock.instant());
    }

    @Test
    void theCountdownOfLaterQuestionsStartsWhenTheClientReportsItNotWhenTheyWereSent() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(1));
        answer(1, 0);
        // The client plays feedback audio first; it reports Q2's start 3 seconds after receiving it.
        clock.advance(Duration.ofSeconds(3));
        startTimer(2);
        clock.advance(Duration.ofSeconds(9));

        assertThat(answer(2, 0).result()).isEqualTo(AnswerResult.CORRECT);
    }

    @Test
    void listenAgainRestartsTheCountdownFromTheMomentOfTheCall() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(7));
        service.startTimer(anonymousId, session.getSessionId(), new TimerStartRequest(question(1).getId(), true));
        clock.advance(Duration.ofSeconds(9));   // 16 s after the first start: late without the restart, in time with it

        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.CORRECT);
    }

    @Test
    void afterARestartTheNewDeadlineStillApplies() {
        startTimer(1);
        service.startTimer(anonymousId, session.getSessionId(), new TimerStartRequest(question(1).getId(), true));
        clock.advance(Duration.ofSeconds(9).plusMillis(1));

        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.TIMEOUT);
    }

    @Test
    void listenAgainIsLimitedToTwoRestartsPerQuestion() {
        startTimer(1);
        TimerStartRequest restart = new TimerStartRequest(question(1).getId(), true);
        clock.advance(Duration.ofSeconds(5));
        service.startTimer(anonymousId, session.getSessionId(), restart);   // 1st restart: accepted
        clock.advance(Duration.ofSeconds(5));
        service.startTimer(anonymousId, session.getSessionId(), restart);   // 2nd restart: accepted
        clock.advance(Duration.ofSeconds(5));
        service.startTimer(anonymousId, session.getSessionId(), restart);   // 3rd: ignored, the 2nd deadline stands
        clock.advance(Duration.ofSeconds(4).plusMillis(1));                // just past the 2nd restart deadline (8 s + 1 s grace)

        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.TIMEOUT);
    }

    @Test
    void aClientCannotGainTimeByReportingTheStartLate() {
        // Question 1 has no audio: the start can be at most presented + 3 s tolerance, however late the report arrives.
        clock.advance(Duration.ofSeconds(60));
        startTimer(1);                       // reported a minute late
        clock.advance(Duration.ofSeconds(1));

        // effective start = START + 3 s, deadline = START + 3 + 8 + 1 = START + 12 s; now = START + 61 s
        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.TIMEOUT);
    }

    @Test
    void withoutAnyReportTheLatestAllowedStartIsUsed() {
        clock.advance(Duration.ofSeconds(12));
        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.CORRECT);          // START+12 = deadline of START+3+8+1

        startTimer(2);                                                              // keeps the test readable
        clock.advance(Duration.ofSeconds(9).plusMillis(1));
        assertThat(answer(2, 0).result()).isEqualTo(AnswerResult.TIMEOUT);
    }

    @Test
    void theQuestionAudioLengthIsAddedToTheLatestAllowedStart() {
        MediaAsset voice = MediaAsset.builder().name("q").storageUrl("https://x/q.mp3").durationMs(5000).build();
        question(1).setQuestionVoice(voice);

        clock.advance(Duration.ofSeconds(5 + 3 + 8));      // presented + audio + tolerance + limit, still inside the 1 s grace
        assertThat(answer(1, 0).result()).isEqualTo(AnswerResult.CORRECT);
    }

    @Test
    void theFirstTimerReportWinsAndRepeatsAreIgnored() {
        startTimer(1);
        Instant first = session.getSessionQuestions().get(0).getTimerStartedAt();
        clock.advance(Duration.ofSeconds(2));

        startTimer(1);

        assertThat(first).isEqualTo(START);
        assertThat(session.getSessionQuestions().get(0).getTimerStartedAt()).isEqualTo(first);
    }

    @Test
    void theTimerCanOnlyBeStartedForTheCurrentUnansweredQuestion() {
        assertThatThrownBy(() -> startTimer(3)).isInstanceOf(QuestionNotFoundException.class);
        startTimer(1);
        answer(1, 0);
        assertThatThrownBy(() -> startTimer(1)).isInstanceOf(QuestionAlreadyAnsweredException.class);
        session.setStatus(SessionStatus.ABANDONED);
        assertThatThrownBy(() -> startTimer(2)).isInstanceOf(SessionNotActiveException.class);
    }

    // ------------------------------------------------------------- validation

    @Test
    void duplicateAnswerIsRejectedAndScoreIsNotIncrementedTwice() {
        startTimer(1);
        answer(1, 0);

        assertThatThrownBy(() -> answer(1, 0)).isInstanceOf(QuestionAlreadyAnsweredException.class);

        assertThat(session.getScore()).isEqualTo((short) 1);
        verify(playerRepository, times(1)).addStats(any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void answeringATimedOutQuestionIsRejectedWithAnswerTimeout() {
        startTimer(1);
        clock.advance(Duration.ofSeconds(10));
        service.submitTimeout(anonymousId, session.getSessionId(), new SubmitTimeoutRequest(question(1).getId()));

        assertThatThrownBy(() -> answer(1, 0)).isInstanceOf(AnswerTimeoutException.class);
    }

    @Test
    void anOptionOfAnotherQuestionOfTheSessionIsRejected() {
        startTimer(1);

        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), option(2, 0).optionId())))
                .isInstanceOf(OptionNotBelongToQuestionException.class);
        assertThat(session.getSessionQuestions().get(0).getResult()).isNull();
    }

    @Test
    void anOptionThatIsInNoSnapshotIsRejected() {
        startTimer(1);

        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), UUID.randomUUID())))
                .isInstanceOf(InvalidOptionException.class);
    }

    @Test
    void anAnswerItemIdIsNotAnOptionIdAndIsRejected() {
        startTimer(1);
        UUID answerItemId = session.getSessionQuestions().get(0).getOptionsSnapshot().get(0).answerItemId();

        assertThatThrownBy(() -> service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(1).getId(), answerItemId)))
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
                new SubmitAnswerRequest(question(1).getId(), option(1, 0).optionId())))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.getSession(otherAnonymousId, session.getSessionId()))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.startTimer(otherAnonymousId, session.getSessionId(),
                new TimerStartRequest(question(1).getId())))
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
            startTimer(number);
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
            startTimer(number);
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
    void getSessionResumesWithTheSameOptionsInTheSameOrder() {
        startTimer(1);
        answer(1, 0);

        GameSessionResponse response = service.getSession(anonymousId, session.getSessionId());

        assertThat(response.currentQuestionNumber()).isEqualTo(2);
        assertThat(response.question().id()).isEqualTo(question(2).getId());
        assertThat(response.timeLimitSeconds()).isEqualTo(8);
        assertThat(response.question().options()).extracting(o -> o.id())
                .containsExactly(option(2, 0).optionId(), option(2, 1).optionId(), option(2, 2).optionId(), option(2, 3).optionId());
    }

    // ----------------------------------------------------------------- create

    @Test
    void createSessionBuildsFiveUniqueQuestionsEachWithItsOwnSnapshot() {
        List<GameQuestion> pool = stubCreate(8);

        GameSessionResponse response = service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null));

        ArgumentCaptor<GameSession> saved = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionRepository).save(saved.capture());
        GameSession created = saved.getValue();
        assertThat(created.getSessionQuestions()).hasSize(5);
        assertThat(created.getSessionQuestions().stream().map(sq -> sq.getQuestionNumber().intValue()).toList())
                .containsExactly(1, 2, 3, 4, 5);
        Set<UUID> questionIds = new HashSet<>();
        created.getSessionQuestions().forEach(sq -> {
            questionIds.add(sq.getQuestion().getId());
            assertThat(sq.getOptionsSnapshot()).hasSize(4);
            assertThat(sq.getOptionsSnapshot().stream().filter(SnapshotOption::isCorrect)).hasSize(1);
        });
        assertThat(questionIds).hasSize(5);
        assertThat(created.getStatus()).isEqualTo(SessionStatus.STARTED);
        assertThat(created.getStartedAt()).isEqualTo(START);
        assertThat(created.getSessionQuestions().get(0).getPresentedAt()).isEqualTo(START);
        assertThat(created.getSessionQuestions().get(1).getPresentedAt()).isNull();
        assertThat(response.question().id()).isEqualTo(created.getSessionQuestions().get(0).getQuestion().getId());
        assertThat(response.currentQuestionNumber()).isEqualTo(1);
        assertThat(response.timeLimitSeconds()).isEqualTo(8);
        assertThat(pool).hasSize(8);
        verify(activityLogService).record(eq(ActivityEventType.START_GAME), eq(player), eq(created), eq(topic),
                isNull(), isNull(), isNull());
    }

    @Test
    void createSessionDrawsFromTheWholeTopicSubtree() {
        List<GameQuestion> pool = stubCreate(6);
        UUID childTopic = UUID.randomUUID();
        when(topicRepository.findActiveSubtreeIds(topic.getId())).thenReturn(List.of(topic.getId(), childTopic));
        when(questionRepository.findCandidateIds(eq(List.of(topic.getId(), childTopic)), eq(mode.getId()), any(String[].class)))
                .thenReturn(pool.stream().map(GameQuestion::getId).toList());

        service.createSession(anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null));

        verify(questionRepository).findCandidateIds(eq(List.of(topic.getId(), childTopic)), eq(mode.getId()), any(String[].class));
    }

    @Test
    void createSessionPassesTheAgeFilterToTheCandidateQuery() {
        List<GameQuestion> pool = stubCreate(6);
        // AGE_4_5 reads the two pools separately (60 % AGE_4_5 / 40 % AGE_1_3); here only the AGE_4_5 pool has questions.
        when(questionRepository.findCandidateIds(any(), any(), argThat((String[] groups) -> groups != null
                && groups.length == 1 && groups[0].equals("AGE_4_5"))))
                .thenReturn(pool.stream().map(GameQuestion::getId).toList());

        service.createSession(anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), AgeGroup.AGE_4_5));

        verify(questionRepository).findCandidateIds(any(), eq(mode.getId()), argThat((String[] groups) -> groups != null
                && groups.length == 1 && groups[0].equals("AGE_1_3")));
    }

    @Test
    void aQuestionThatCannotProduceOptionsIsSkippedAndReplaced() {
        List<GameQuestion> pool = stubCreate(7);
        when(optionGenerator.generate(pool.get(0))).thenThrow(new InsufficientDistractorsException("pool shrank"));
        // The first candidate may be any of the 7 after shuffling, so make one specific question always fail.
        GameSession created = createAndCapture();

        assertThat(created.getSessionQuestions()).hasSize(5);
        assertThat(created.getSessionQuestions().stream().map(SessionQuestion::getQuestion)).doesNotContain(pool.get(0));
    }

    @Test
    void createSessionFailsWhenFewerThanFiveQuestionsCanProduceOptions() {
        List<GameQuestion> pool = stubCreate(6);
        when(optionGenerator.generate(pool.get(0))).thenThrow(new InsufficientDistractorsException("pool shrank"));
        when(optionGenerator.generate(pool.get(1))).thenThrow(new InsufficientDistractorsException("pool shrank"));

        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(InsufficientQuestionsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSessionNeedsAtLeastFiveCandidateQuestions() {
        stubCreate(4);

        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(InsufficientQuestionsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSessionRejectsInactiveTopicAndTopicWithoutActiveSubtree() {
        stubCreate(6);
        when(topicRepository.findActiveSubtreeIds(topic.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(TopicNotPlayableException.class);

        topic.setActive(false);
        assertThatThrownBy(() -> service.createSession(
                anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null)))
                .isInstanceOf(TopicNotPlayableException.class);
    }

    // ------------------------------------------------------------- fixtures

    private GameSession createAndCapture() {
        service.createSession(anonymousId, new CreateGameSessionRequest(topic.getId(), mode.getId(), null));
        ArgumentCaptor<GameSession> saved = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionRepository).save(saved.capture());
        return saved.getValue();
    }

    /** Stubs everything createSession needs and returns the pool of {@code size} candidate questions. */
    private List<GameQuestion> stubCreate(int size) {
        List<GameQuestion> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            GameQuestion question = newQuestion();
            pool.add(question);
            when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
            when(optionGenerator.generate(question)).thenAnswer(invocation -> newSnapshot());
        }
        when(playerService.resolveOrCreate(anonymousId)).thenReturn(player);
        when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
        when(gameModeRepository.findById(mode.getId())).thenReturn(Optional.of(mode));
        when(topicRepository.findActiveSubtreeIds(topic.getId())).thenReturn(List.of(topic.getId()));
        when(questionRepository.findCandidateIds(any(), eq(mode.getId()), any(String[].class)))
                .thenReturn(pool.stream().map(GameQuestion::getId).toList());
        return pool;
    }

    private void startTimer(int questionNumber) {
        service.startTimer(anonymousId, session.getSessionId(), new TimerStartRequest(question(questionNumber).getId()));
    }

    private AnswerResponse answer(int questionNumber, int optionIndex) {
        return service.submitAnswer(anonymousId, session.getSessionId(),
                new SubmitAnswerRequest(question(questionNumber).getId(), option(questionNumber, optionIndex).optionId()));
    }

    private GameQuestion question(int questionNumber) {
        return session.getSessionQuestions().get(questionNumber - 1).getQuestion();
    }

    /** Snapshot option {@code optionIndex} (0-based) of question {@code questionNumber}; index 0 is the correct one. */
    private SnapshotOption option(int questionNumber, int optionIndex) {
        return session.getSessionQuestions().get(questionNumber - 1).getOptionsSnapshot().get(optionIndex);
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
                    .question(newQuestion())
                    .questionNumber((short) number)
                    .optionsSnapshot(newSnapshot())
                    .presentedAt(number == 1 ? START : null)
                    .build());
        }
        return created;
    }

    private GameQuestion newQuestion() {
        AnswerItem correct = AnswerItem.builder().code("C_" + UUID.randomUUID()).name("Bird").build();
        correct.setId(UUID.randomUUID());
        GameQuestion question = GameQuestion.builder()
                .topic(topic).gameMode(mode).questionText("Which animal can fly?").correctAnswerItem(correct)
                .difficulty((short) 1).minAge((short) 1).maxAge((short) 5).build();
        question.setId(UUID.randomUUID());
        return question;
    }

    /** 4 options: index 0 is the correct one, indexes 1-3 are wrong. */
    private List<SnapshotOption> newSnapshot() {
        String[] names = {"Bird", "Dog", "Cat", "Cow"};
        List<SnapshotOption> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            options.add(new SnapshotOption(UUID.randomUUID(), UUID.randomUUID(), names[i], null, null, i + 1, i == 0));
        }
        return options;
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
