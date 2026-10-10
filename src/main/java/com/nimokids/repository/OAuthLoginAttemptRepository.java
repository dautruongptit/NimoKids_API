package com.nimokids.repository;

import com.nimokids.entity.OAuthLoginAttempt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OAuthLoginAttemptRepository extends JpaRepository<OAuthLoginAttempt, UUID> {

    Optional<OAuthLoginAttempt> findByStateHash(byte[] stateHash);

    /** Single use: only the first call that finds the attempt unused and not expired wins (returns 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OAuthLoginAttempt a set a.consumedAt = :now where a.id = :id and a.consumedAt is null and a.expiresAt > :now")
    int consume(UUID id, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OAuthLoginAttempt a where a.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
