package com.nimokids.service.impl;

import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.exception.AnonymousPlayerRequiredException;
import com.nimokids.repository.AnonymousPlayerRepository;
import com.nimokids.service.PlayerService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final AnonymousPlayerRepository playerRepository;
    private final Clock clock;

    @Override
    @Transactional
    public AnonymousPlayer resolveOrCreate(UUID anonymousId) {
        if (anonymousId == null) {
            throw new AnonymousPlayerRequiredException();
        }
        return playerRepository.findByAnonymousId(anonymousId).orElseGet(() -> {
            playerRepository.insertIfAbsent(anonymousId, clock.instant());
            return playerRepository.findByAnonymousId(anonymousId)
                    .orElseThrow(() -> new IllegalStateException("Anonymous player was not created"));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnonymousPlayer> find(UUID anonymousId) {
        if (anonymousId == null) {
            throw new AnonymousPlayerRequiredException();
        }
        return playerRepository.findByAnonymousId(anonymousId);
    }
}
