package com.nimokids.dto.response;

import java.util.UUID;

public record TopicResponse(
        UUID id,
        String code,
        String name,
        String slug,
        String description,
        String iconUrl,
        String coverImageUrl,
        Integer minAge,
        Integer maxAge,
        Integer displayOrder) {
}
