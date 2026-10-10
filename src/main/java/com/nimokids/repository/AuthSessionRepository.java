package com.nimokids.repository;

import com.nimokids.entity.AuthSession;
import com.nimokids.entity.enums.AuthSessionStatus;
import com.nimokids.entity.enums.SessionEndReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findByAdminUserIdAndStatusOrderByCreatedAtAsc(UUID adminUserId, AuthSessionStatus status);

    List<AuthSession> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, AuthSessionStatus status);

    /**
     * Records activity without a read: only when the last write is older than {@code threshold}, so this is cheap
     * to call and writes at most once per throttle window. Returns the number of rows changed (0 or 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s set s.lastSeenAt = :now, s.idleExpiresAt = :idleExpiresAt, s.updatedAt = :now
            where s.id = :id and s.status = com.nimokids.entity.enums.AuthSessionStatus.ACTIVE and s.lastSeenAt < :threshold
            """)
    int touch(UUID id, Instant now, Instant idleExpiresAt, Instant threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s set s.status = com.nimokids.entity.enums.AuthSessionStatus.EXPIRED, s.endedAt = :now,
                s.endedReason = :reason, s.updatedAt = :now
            where s.status = com.nimokids.entity.enums.AuthSessionStatus.ACTIVE and s.idleExpiresAt < :now
            """)
    int expireIdle(Instant now, SessionEndReason reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s set s.status = com.nimokids.entity.enums.AuthSessionStatus.EXPIRED, s.endedAt = :now,
                s.endedReason = :reason, s.updatedAt = :now
            where s.status = com.nimokids.entity.enums.AuthSessionStatus.ACTIVE and s.absoluteExpiresAt < :now
            """)
    int expireAbsolute(Instant now, SessionEndReason reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuthSession s where s.status <> com.nimokids.entity.enums.AuthSessionStatus.ACTIVE and s.endedAt < :before")
    int deleteEndedBefore(Instant before);
}
