package com.nimokids.service;

import com.nimokids.dto.request.ActivityBatchRequest;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.ActivityEventType;
import java.util.UUID;

/** Analytics only. Activity logs are never used to decide score or session state. */
public interface ActivityLogService {

    /** Records a server-authoritative event inside the caller's transaction. */
    void record(
            ActivityEventType eventType,
            AnonymousPlayer player,
            GameSession session,
            Topic topic,
            GameQuestion question,
            UUID optionId,
            Integer durationMs);

    /** Stores client-side interaction events (audio, navigation). Server-decided events are rejected. */
    void recordBatch(UUID anonymousId, ActivityBatchRequest request);
}
