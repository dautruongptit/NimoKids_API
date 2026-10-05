package com.nimokids.dto.response;

import java.util.UUID;

/** Revealed only after the question has been answered or has timed out. */
public record CorrectAnswerResponse(UUID id, String text, String voice) {
}
