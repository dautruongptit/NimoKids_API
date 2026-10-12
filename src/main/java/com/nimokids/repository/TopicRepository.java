package com.nimokids.repository;

import com.nimokids.entity.Topic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByCode(String code);

    Optional<Topic> findBySlug(String slug);

    List<Topic> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * The topic and all of its ACTIVE descendants (a topic with an inactive ancestor below the root is not reached).
     * Choosing a parent topic draws questions from every id returned here. UNION (not UNION ALL) makes the recursion
     * terminate even if bad data ever contained a cycle.
     */
    @Query(value = """
            WITH RECURSIVE subtree(id) AS (
                SELECT id FROM topics WHERE id = :rootId AND is_active = TRUE
                  AND (CAST(:mode AS text) IS NULL OR CAST(:mode AS text) = ANY(supported_modes))
                UNION
                SELECT t.id FROM topics t JOIN subtree s ON t.parent_id = s.id WHERE t.is_active = TRUE
                  AND (CAST(:mode AS text) IS NULL OR CAST(:mode AS text) = ANY(t.supported_modes))
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<UUID> findActiveSubtreeIds(UUID rootId, String mode);

    /** Same, for every language mode (no mode filter). */
    default List<UUID> findActiveSubtreeIds(UUID rootId) {
        return findActiveSubtreeIds(rootId, null);
    }

    /** MIX mode: every active topic reachable from an active ROOT topic, in ONE query (not one per root). */
    @Query(value = """
            WITH RECURSIVE subtree(id) AS (
                SELECT id FROM topics WHERE parent_id IS NULL AND is_active = TRUE
                  AND (CAST(:mode AS text) IS NULL OR CAST(:mode AS text) = ANY(supported_modes))
                UNION
                SELECT t.id FROM topics t JOIN subtree s ON t.parent_id = s.id WHERE t.is_active = TRUE
                  AND (CAST(:mode AS text) IS NULL OR CAST(:mode AS text) = ANY(t.supported_modes))
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<UUID> findAllActiveSubtreeIds(String mode);

    default List<UUID> findAllActiveSubtreeIds() {
        return findAllActiveSubtreeIds(null);
    }

    /** Active topics that can be played in {@code mode} (its name is in supported_modes), in display order. */
    @Query(value = """
            SELECT * FROM topics
            WHERE is_active = TRUE AND CAST(:mode AS text) = ANY(supported_modes)
            ORDER BY display_order ASC
            """, nativeQuery = true)
    List<Topic> findActiveSupportingMode(String mode);
}
