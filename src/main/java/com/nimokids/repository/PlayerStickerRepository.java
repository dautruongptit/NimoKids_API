package com.nimokids.repository;

import com.nimokids.entity.PlayerSticker;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlayerStickerRepository extends JpaRepository<PlayerSticker, UUID> {

    /** Stickers earned in the session identified by game_sessions.id. */
    List<PlayerSticker> findBySessionId(UUID sessionId);

    List<PlayerSticker> findByPlayerIdOrderByEarnedAtDesc(UUID playerId);

    /**
     * Awards a sticker at most once per player (UNIQUE(player_id, sticker_id)).
     * Returns 1 when newly awarded and 0 when the player already owned it, without raising an error
     * that would roll back the surrounding game transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into player_stickers (id, player_id, sticker_id, session_id, earned_at, created_at, updated_at)
            values (gen_random_uuid(), :playerId, :stickerId, :sessionId, :now, :now, :now)
            on conflict (player_id, sticker_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID playerId, UUID stickerId, UUID sessionId, Instant now);
}
