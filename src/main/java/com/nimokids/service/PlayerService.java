package com.nimokids.service;

import com.nimokids.entity.AnonymousPlayer;
import java.util.Optional;
import java.util.UUID;

public interface PlayerService {

    /** Returns the player for this anonymous id, creating it on first use. Safe under concurrent first requests. */
    AnonymousPlayer resolveOrCreate(UUID anonymousId);

    Optional<AnonymousPlayer> find(UUID anonymousId);
}
