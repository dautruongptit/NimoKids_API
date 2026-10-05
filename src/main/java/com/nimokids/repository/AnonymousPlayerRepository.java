package com.nimokids.repository;

import com.nimokids.entity.AnonymousPlayer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnonymousPlayerRepository extends JpaRepository<AnonymousPlayer, UUID> {

    Optional<AnonymousPlayer> findByAnonymousId(UUID anonymousId);

    /** Race-safe creation: two first requests with the same anonymous id cannot violate the unique constraint. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into anonymous_players (id, anonymous_id, first_seen_at, last_seen_at, created_at, updated_at)
            values (gen_random_uuid(), :anonymousId, :now, :now, :now, :now)
            on conflict (anonymous_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID anonymousId, Instant now);

    /** Atomic counters, so concurrent sessions of the same player never lose an update. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update AnonymousPlayer p
            set p.totalGames = p.totalGames + :games,
                p.totalQuestions = p.totalQuestions + :questions,
                p.totalCorrect = p.totalCorrect + :correct,
                p.lastSeenAt = :now,
                p.updatedAt = :now
            where p.id = :id
            """)
    int addStats(UUID id, int games, int questions, int correct, Instant now);
}
