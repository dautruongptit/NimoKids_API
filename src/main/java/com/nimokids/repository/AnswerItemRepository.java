package com.nimokids.repository;

import com.nimokids.entity.AnswerItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnswerItemRepository extends JpaRepository<AnswerItem, UUID> {

    Optional<AnswerItem> findByCode(String code);

    /**
     * Random active items whose tags contain EVERY tag of {@code match} (explicit state matching, JSONB containment).
     *
     * This is the only way wrong answers are found. It deliberately has no tag negation (no "NOT @>", no "<>" on
     * metadata): negation would also accept untagged or wrongly tagged items. Only the ids of items that were
     * already chosen (the correct answer and earlier distractors) are excluded. The {@code @>} operator is served
     * by the GIN index idx_answer_items_metadata.
     *
     * @param match       JSON object such as {"category":"animal","can_fly":false}
     * @param excludedIds never empty: always contains the correct answer item
     */
    @Query(value = """
            SELECT * FROM answer_items
            WHERE is_active = TRUE
              AND metadata @> CAST(:match AS jsonb)
              AND id NOT IN (:excludedIds)
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<AnswerItem> findRandomMatching(String match, Collection<UUID> excludedIds, int limit);
}
