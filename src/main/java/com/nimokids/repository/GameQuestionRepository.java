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
     * and correct answer item active, the object sound present when the mode needs one, the age group matching, and
     * distractor rules present.
     *
     * {@code ageGroups} contains the PostgreSQL enum literals to filter on (e.g. ['AGE_1_3'] or ['AGE_1_3','AGE_4_5']).
     * An empty collection means no age filter.
     *
     * Whether the distractor pool is really large enough is checked when the options are generated (and on admin
     * save); a question that cannot produce 4 options is skipped and replaced.
     */
    @Query(value = """
            SELECT q.id
            FROM game_questions q
            JOIN game_modes m ON m.id = q.game_mode_id
            JOIN answer_items a ON a.id = q.correct_answer_item_id
            LEFT JOIN question_templates qt ON qt.id = q.template_id
            WHERE q.topic_id IN (:topicIds)
              AND q.game_mode_id = :gameModeId
              AND q.is_active = TRUE AND m.is_active = TRUE AND a.is_active = TRUE
              AND q.age_group = ANY(CAST(:ageGroups AS age_group[]))
              AND (m.code <> :soundModeCode OR q.object_sound_id IS NOT NULL)
              AND jsonb_typeof(q.metadata -> 'distractor_rules') = 'array'
              -- Short template questions are preferred: once a topic (and age group) has at least :templateOnlyFrom of
              -- them, the long hand-written (CURATED) questions are not offered any more. 0 turns the rule off.
              AND (:templateOnlyFrom <= 0 OR qt.kind = 'TEMPLATE' OR (
                    SELECT count(*) FROM game_questions g
                    JOIN question_templates gt ON gt.id = g.template_id AND gt.kind = 'TEMPLATE'
                    WHERE g.topic_id = q.topic_id AND g.game_mode_id = q.game_mode_id
                      AND g.age_group = q.age_group AND g.is_active = TRUE) < :templateOnlyFrom)
            """, nativeQuery = true)
    List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, String[] ageGroups, String soundModeCode,
                                int templateOnlyFrom);

    /** Without the template preference (every active question is a candidate). */
    default List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, String[] ageGroups, String soundModeCode) {
        return findCandidateIds(topicIds, gameModeId, ageGroups, soundModeCode, 0);
    }

    /** What a game session uses: short template questions are preferred (see {@link GameConstants#TEMPLATE_ONLY_FROM}). */
    default List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, String[] ageGroups) {
        return findCandidateIds(topicIds, gameModeId, ageGroups, GameConstants.ANIMAL_SOUND_MODE_CODE,
                GameConstants.TEMPLATE_ONLY_FROM);
    }

    /**
     * Candidates filtered by a single age group (convenience overload for legacy callers).
     */
    default List<UUID> findCandidateIds(Collection<UUID> topicIds, UUID gameModeId, int age) {
        String[] groups = age == 0
                ? new String[]{"AGE_1_3", "AGE_4_5"}
                : age <= 3
                        ? new String[]{"AGE_1_3"}
                        : new String[]{"AGE_1_3", "AGE_4_5"};
        return findCandidateIds(topicIds, gameModeId, groups, GameConstants.ANIMAL_SOUND_MODE_CODE);
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

    /** Loads questions with everything a session snapshot reads, in ONE query (instead of ~6 lazy loads per question). */
    @Query("""
            SELECT DISTINCT q FROM GameQuestion q
            LEFT JOIN FETCH q.topic
            LEFT JOIN FETCH q.template
            LEFT JOIN FETCH q.questionVoice
            LEFT JOIN FETCH q.objectSound
            LEFT JOIN FETCH q.correctAnswerItem ci
            LEFT JOIN FETCH ci.image
            LEFT JOIN FETCH ci.voice
            WHERE q.id IN :ids
            """)
    List<GameQuestion> findAllWithDetails(Collection<UUID> ids);
}
