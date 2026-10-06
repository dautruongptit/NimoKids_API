package com.nimokids.repository;

import com.nimokids.entity.GameQuestion;
import com.nimokids.util.GameConstants;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameQuestionRepository extends JpaRepository<GameQuestion, UUID> {

    /**
     * Ids of questions that can be asked for a game mode inside a set of topics (a topic subtree): question, game mode
     * and correct answer item active, the object sound present when the mode needs one, the age range matching when
     * {@code age} is not 0 (0 = no age filter), and distractor rules present.
     *
     * Whether the distractor pool is really large enough is checked when the options are generated (and on admin
     * save); a question that cannot produce 4 options is skipped and replaced.
     */
    @Query(value = """
            SELECT q.id
            FROM game_questions q
            JOIN game_modes m ON m.id = q.game_mode_id
            JOIN answer_items a ON a.id = q.correct_answer_item_id
            WHERE q.topic_id IN (:topicIds)
              AND q.game_mode_id = :gameModeId
              AND q.is_active = TRUE AND m.is_active = TRUE AND a.is_active = TRUE
              AND (:age = 0 OR (q.min_age <= :age AND q.max_age >= :age))
              AND (m.code <> :soundModeCode OR q.object_sound_id IS NOT NULL)
              AND jsonb_typeof(q.metadata -> 'distractor_rules') = 'array'
            """, nativeQuery = true)
    List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, int age, String soundModeCode);

    default List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, int age) {
        return findCandidateIds(topicIds, gameModeId, age, GameConstants.ANIMAL_SOUND_MODE_CODE);
    }

    /**
     * Topics that are playable: active, with at least {@code minCount} valid questions in their whole subtree
     * (a parent can be playable through its children). Counted over all game modes.
     */
    @Query(value = """
            WITH RECURSIVE tree(root_id, node_id) AS (
                SELECT id, id FROM topics WHERE is_active = TRUE
                UNION
                SELECT tr.root_id, t.id FROM tree tr JOIN topics t ON t.parent_id = tr.node_id WHERE t.is_active = TRUE
            )
            SELECT tr.root_id
            FROM tree tr
            JOIN game_questions q ON q.topic_id = tr.node_id
            JOIN game_modes m ON m.id = q.game_mode_id
            JOIN answer_items a ON a.id = q.correct_answer_item_id
            WHERE q.is_active = TRUE AND m.is_active = TRUE AND a.is_active = TRUE
              AND (m.code <> :soundModeCode OR q.object_sound_id IS NOT NULL)
              AND jsonb_typeof(q.metadata -> 'distractor_rules') = 'array'
            GROUP BY tr.root_id
            HAVING count(*) >= :minCount
            """, nativeQuery = true)
    List<UUID> findPlayableTopicIds(long minCount, String soundModeCode);

    default List<UUID> findPlayableTopicIds(long minCount) {
        return findPlayableTopicIds(minCount, GameConstants.ANIMAL_SOUND_MODE_CODE);
    }
}
