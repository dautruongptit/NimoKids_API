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
                UNION
                SELECT t.id FROM topics t JOIN subtree s ON t.parent_id = s.id WHERE t.is_active = TRUE
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<UUID> findActiveSubtreeIds(UUID rootId);
}
