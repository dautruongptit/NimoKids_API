package com.nimokids.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** POST/PUT /api/v1/admin/topics. */
public record AdminTopicRequest(
        @NotBlank(message = "must not be blank") @Size(max = 50, message = "must be at most 50 characters") String code,
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters") String name,
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, digits and hyphens") String slug,
        String description,
        @Size(max = 500, message = "must be at most 500 characters") String iconUrl,
        @Size(max = 500, message = "must be at most 500 characters") String coverImageUrl,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer minAge,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer maxAge,
        @NotNull(message = "must not be null") @PositiveOrZero(message = "must be zero or positive") Integer displayOrder,
        Boolean isActive) {

    @JsonIgnore
    @AssertTrue(message = "maxAge must be greater than or equal to minAge")
    public boolean isAgeRangeValid() {
        return minAge == null || maxAge == null || maxAge >= minAge;
    }
}
