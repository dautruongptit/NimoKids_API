package com.nimokids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdminQuestionOptionRequest(
        @NotBlank(message = "must not be blank") @Size(max = 255, message = "must be at most 255 characters") String text,
        UUID imageId,
        UUID voiceId,
        @NotNull(message = "must not be null") Boolean isCorrect,
        @NotNull(message = "must not be null") @PositiveOrZero(message = "must be zero or positive") Integer displayOrder) {
}
