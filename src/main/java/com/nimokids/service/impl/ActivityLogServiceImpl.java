package com.nimokids.service.impl;

import com.nimokids.dto.request.ActivityBatchRequest;
import com.nimokids.dto.request.ActivityEventRequest;
import com.nimokids.dto.response.FieldErrorDetail;
import com.nimokids.entity.ActivityLog;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.QuestionOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.ActivityEventType;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.exception.SessionNotFoundException;
import com.nimokids.repository.ActivityLogRepository;
import com.nimokids.repository.GameQuestionRepository;
import com.nimokids.repository.GameSessionRepository;
import com.nimokids.repository.QuestionOptionRepository;
import com.nimokids.service.ActivityLogService;
import com.nimokids.service.PlayerService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    /** Events the browser may report. ANSWER_*, START_GAME and FINISH_GAME are written by the server only. */
    private static final Set<ActivityEventType> CLIENT_EVENTS = EnumSet.of(
            ActivityEventType.VIEW_QUESTION,
            ActivityEventType.PLAY_QUESTION_AUDIO,
            ActivityEventType.SELECT_ANSWER,
            ActivityEventType.PLAY_ANSWER_AUDIO,
            ActivityEventType.PLAY_OBJECT_SOUND,
            ActivityEventType.NEXT_QUESTION,
            ActivityEventType.PLAY_AGAIN,
            ActivityEventType.GO_HOME);

    private final ActivityLogRepository activityLogRepository;
    private final GameSessionRepository sessionRepository;
    private final GameQuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final PlayerService playerService;
    private final Clock clock;

    @Override
    @Transactional
    public void record(
            ActivityEventType eventType,
            AnonymousPlayer player,
            GameSession session,
            Topic topic,
            GameQuestion question,
            QuestionOption option,
            Integer durationMs) {
        activityLogRepository.save(ActivityLog.builder()
                .eventType(eventType)
                .player(player)
                .session(session)
                .topic(topic)
                .question(question)
                .option(option)
                .durationMs(durationMs)
                .eventTime(clock.instant())
                .build());
    }

    @Override
    @Transactional
    public void recordBatch(UUID anonymousId, ActivityBatchRequest request) {
        rejectServerOnlyEvents(request.events());

        AnonymousPlayer player = playerService.resolveOrCreate(anonymousId);
        GameSession session = null;
        if (request.sessionId() != null) {
            session = sessionRepository.findBySessionId(request.sessionId())
                    .filter(found -> found.getPlayer().getId().equals(player.getId()))
                    .orElseThrow(SessionNotFoundException::new);
        }

        Instant now = clock.instant();
        Map<UUID, GameQuestion> questions = new HashMap<>();
        Map<UUID, QuestionOption> options = new HashMap<>();
        List<ActivityLog> logs = new ArrayList<>();
        for (ActivityEventRequest event : request.events()) {
            logs.add(ActivityLog.builder()
                    .eventType(event.eventType())
                    .player(player)
                    .session(session)
                    .topic(session == null ? null : session.getTopic())
                    .question(event.questionId() == null ? null : questions.computeIfAbsent(event.questionId(),
                            id -> questionRepository.findById(id)
                                    .orElseThrow(() -> new ResourceNotFoundException("Question", id))))
                    .option(event.optionId() == null ? null : options.computeIfAbsent(event.optionId(),
                            id -> optionRepository.findById(id)
                                    .orElseThrow(() -> new ResourceNotFoundException("Option", id))))
                    .durationMs(event.durationMs())
                    .eventTime(event.eventTime() != null ? event.eventTime() : now)
                    .metadata(event.metadata())
                    .build());
        }
        activityLogRepository.saveAll(logs);
    }

    private static void rejectServerOnlyEvents(List<ActivityEventRequest> events) {
        List<FieldErrorDetail> violations = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            ActivityEventType type = events.get(i).eventType();
            if (type != null && !CLIENT_EVENTS.contains(type)) {
                violations.add(new FieldErrorDetail("events[" + i + "].eventType", "is decided by the server"));
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Validation failed", violations);
        }
    }
}
