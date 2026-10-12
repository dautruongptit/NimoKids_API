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
     * @param basicEnglishOnly true = only basic English words (is_basic_english), false = the whole vocabulary
     */
    @Query(value = """
            SELECT * FROM answer_items
            WHERE is_active = TRUE
              AND metadata @> CAST(:match AS jsonb)
              AND id NOT IN (:excludedIds)
              AND (:basicEnglishOnly = FALSE OR is_basic_english = TRUE)
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<AnswerItem> findRandomMatching(String match, Collection<UUID> excludedIds, int limit, boolean basicEnglishOnly);

    default List<AnswerItem> findRandomMatching(String match, Collection<UUID> excludedIds, int limit) {
        return findRandomMatching(match, excludedIds, limit, false);
    }

    /** Loads the image and voice of the given items in ONE query so reading them later costs no extra round trips. */
    @Query("SELECT DISTINCT a FROM AnswerItem a LEFT JOIN FETCH a.image LEFT JOIN FETCH a.voice WHERE a.id IN :ids")
    List<AnswerItem> findAllWithMedia(Collection<UUID> ids);

    /**
     * Random active items whose tags contain every tag of {@code match}. Used by dynamic questions to pick the object to
     * count and the number item that equals the count (no item to exclude, unlike {@link #findRandomMatching}).
     */
    @Query(value = """
            SELECT * FROM answer_items
            WHERE is_active = TRUE
              AND metadata @> CAST(:match AS jsonb)
              AND (:basicEnglishOnly = FALSE OR is_basic_english = TRUE)
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<AnswerItem> findRandomActive(String match, int limit, boolean basicEnglishOnly);

    default List<AnswerItem> findRandomActive(String match, int limit) {
        return findRandomActive(match, limit, false);
    }
}
