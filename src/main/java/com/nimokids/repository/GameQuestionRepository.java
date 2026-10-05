package com.nimokids.repository;

import com.nimokids.entity.GameQuestion;
import com.nimokids.util.GameConstants;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameQuestionRepository extends JpaRepository<GameQuestion, UUID> {

    /**
     * Ids of playable questions of a topic and game mode (master 5.3, business-rules BR-002):
     * question, topic and mode active, at least 2 options, exactly 1 correct option, required object sound
     * present, and inside the age range when {@code age} is not 0 (0 means "no age filter").
     */
    @Query("""
            select q.id from GameQuestion q
            where q.topic.id = :topicId
              and q.gameMode.id = :gameModeId
              and q.active = true and q.topic.active = true and q.gameMode.active = true
              and (:age = 0 or (q.minAge <= :age and q.maxAge >= :age))
              and (q.gameMode.code <> :soundModeCode or q.objectSound is not null)
              and (select count(o) from QuestionOption o where o.question = q) >= 2
              and (select count(o) from QuestionOption o where o.question = q and o.correct = true) = 1
            """)
    List<UUID> findPlayableIds(UUID topicId, UUID gameModeId, int age, String soundModeCode);

    default List<UUID> findPlayableIds(UUID topicId, UUID gameModeId, int age) {
        return findPlayableIds(topicId, gameModeId, age, GameConstants.ANIMAL_SOUND_MODE_CODE);
    }

    /** Ids of topics that have at least {@code minCount} playable questions across all game modes. */
    @Query("""
            select q.topic.id from GameQuestion q
            where q.active = true and q.topic.active = true and q.gameMode.active = true
              and (q.gameMode.code <> :soundModeCode or q.objectSound is not null)
              and (select count(o) from QuestionOption o where o.question = q) >= 2
              and (select count(o) from QuestionOption o where o.question = q and o.correct = true) = 1
            group by q.topic.id
            having count(q) >= :minCount
            """)
    List<UUID> findPlayableTopicIds(long minCount, String soundModeCode);

    default List<UUID> findPlayableTopicIds(long minCount) {
        return findPlayableTopicIds(minCount, GameConstants.ANIMAL_SOUND_MODE_CODE);
    }
}
