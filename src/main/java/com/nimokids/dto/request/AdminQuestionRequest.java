package com.nimokids.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * POST/PUT /api/v1/admin/questions.
 * A playable question needs at least 2 options and exactly 1 correct option (master 5.3).
 */
public record AdminQuestionRequest(
        @NotNull(message = "must not be null") UUID topicId,
        @NotNull(message = "must not be null") UUID gameModeId,
        @NotBlank(message = "must not be blank") @Size(max = 500, message = "must be at most 500 characters") String questionText,
        UUID questionVoiceId,
        UUID objectSoundId,
        String explanation,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer difficulty,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer minAge,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer maxAge,
        @Min(value = 1, message = "must be at least 1") Integer timeLimitSeconds,
        Boolean isActive,
        @NotNull(message = "must not be null")
        @Size(min = 2, message = "must contain at least 2 options") List<@Valid @NotNull AdminQuestionOptionRequest> options) {

    @JsonIgnore
    @AssertTrue(message = "maxAge must be greater than or equal to minAge")
    public boolean isAgeRangeValid() {
        return minAge == null || maxAge == null || maxAge >= minAge;
    }

    @JsonIgnore
    @AssertTrue(message = "options must contain exactly one correct option")
    public boolean isSingleCorrectOption() {
        if (options == null) {
            return true;
        }
        return options.stream()
                .filter(option -> option != null && Boolean.TRUE.equals(option.isCorrect()))
                .count() == 1;
    }

    @JsonIgnore
    @AssertTrue(message = "options must have unique displayOrder values")
    public boolean isDisplayOrderUnique() {
        if (options == null) {
            return true;
        }
        var seen = new HashSet<Integer>();
        return options.stream()
                .filter(option -> option != null && option.displayOrder() != null)
                .allMatch(option -> seen.add(option.displayOrder()));
    }
}
