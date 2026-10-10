package com.nimokids.repository;

import com.nimokids.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Row lock: concurrent refreshes of the same token are serialised here. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByHashForUpdate(byte[] hash);

    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    List<RefreshToken> findBySessionId(UUID sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.status = com.nimokids.entity.enums.RefreshTokenStatus.REVOKED
            where t.session.id = :sessionId and t.status in (com.nimokids.entity.enums.RefreshTokenStatus.ACTIVE)
            """)
    int revokeActiveOfSession(UUID sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
