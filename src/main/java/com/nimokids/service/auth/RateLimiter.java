package com.nimokids.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A small fixed-window limiter (in memory, per instance). Redis replaces it when several API instances run (D-07). */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private record Window(Instant start, int count) {
    }

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** True when the call is allowed; false when {@code key} already used its {@code max} calls in the window. */
    public boolean tryAcquire(String key, int max, Duration window) {
        Instant now = clock.instant();
        boolean[] allowed = {true};
        windows.compute(key, (k, current) -> {
            if (current == null || now.isAfter(current.start().plus(window))) {
                return new Window(now, 1);
            }
            if (current.count() >= max) {
                allowed[0] = false;
                return current;
            }
            return new Window(current.start(), current.count() + 1);
        });
        return allowed[0];
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    void evictOldWindows() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(1));
        windows.values().removeIf(w -> w.start().isBefore(cutoff));
    }
}
