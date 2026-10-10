package com.nimokids.service.auth;

import com.nimokids.repository.AuthSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Supplies the active {@link AuthPolicy}, re-read from the database at most every 30 seconds. */
@Service
@RequiredArgsConstructor
public class AuthPolicyService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AuthSettingsRepository repository;
    private final Clock clock;

    private volatile AuthPolicy cached;
    private volatile Instant loadedAt = Instant.MIN;

    public AuthPolicy current() {
        Instant now = clock.instant();
        AuthPolicy policy = cached;
        if (policy == null || now.isAfter(loadedAt.plus(CACHE_TTL))) {
            policy = repository.findById((short) 1).map(row -> AuthPolicy.from(row.getConfig())).orElseGet(AuthPolicy::defaults);
            cached = policy;
            loadedAt = now;
        }
        return policy;
    }

    /** Called after the settings were changed so the next request sees them. */
    public void invalidate() {
        cached = null;
    }
}
