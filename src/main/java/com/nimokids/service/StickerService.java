package com.nimokids.service;

import com.nimokids.dto.response.StickerResponse;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StickerService {

    /**
     * Awards stickers earned by a COMPLETED session, server-side and at most once per player.
     * Must run inside the answer transaction.
     */
    void awardCompletionStickers(GameSession session, AnonymousPlayer player, Instant now);

    /** The player's sticker collection, newest first. Empty for an unknown anonymous id. */
    List<StickerResponse> getPlayerStickers(UUID anonymousId);
}
