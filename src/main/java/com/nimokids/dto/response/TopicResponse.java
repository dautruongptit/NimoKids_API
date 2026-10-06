package com.nimokids.dto.response;

import java.util.UUID;

/**
 * A playable topic. Topics form a tree: {@code parentId} is null for a root. The client builds the tree from the flat
 * list; choosing a parent plays questions from the parent and all of its descendants.
 */
public record TopicResponse(
        UUID id,
        UUID parentId,
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
