package com.nimokids.repository;

import com.nimokids.entity.GameSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    /** Looks up a session by its public identifier (game_sessions.session_id). */
    Optional<GameSession> findBySessionId(UUID sessionId);

    /**
     * Same lookup with a row lock (SELECT ... FOR UPDATE). Concurrent answers for one session are serialized,
     * so only the first request can answer a question and the score can never be incremented twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameSession s where s.sessionId = :sessionId")
    Optional<GameSession> findBySessionIdForUpdate(UUID sessionId);

    /** STARTED -> ABANDONED for sessions without activity since {@code cutoff} (master 5.11). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update GameSession s
            set s.status = com.nimokids.entity.enums.SessionStatus.ABANDONED, s.updatedAt = :now
            where s.status = com.nimokids.entity.enums.SessionStatus.STARTED and s.updatedAt < :cutoff
            """)
    int abandonInactive(Instant cutoff, Instant now);
}
