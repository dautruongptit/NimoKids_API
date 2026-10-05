package com.nimokids.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** POST /api/v1/activities/batch. Audio events may be batched to limit database writes. */
public record ActivityBatchRequest(
        UUID sessionId,
        @NotEmpty(message = "must not be empty")
        @Size(max = 100, message = "must contain at most 100 events") List<@Valid ActivityEventRequest> events) {
}
