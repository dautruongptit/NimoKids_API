package com.nimokids.dto.request;

import com.nimokids.entity.enums.ActivityEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One analytics event. Analytics only: never used to compute score. */
public record ActivityEventRequest(
        @NotNull(message = "must not be null") ActivityEventType eventType,
        UUID questionId,
        UUID optionId,
        Instant eventTime,
        @PositiveOrZero(message = "must be zero or positive") Integer durationMs,
        Map<String, Object> metadata) {
}
