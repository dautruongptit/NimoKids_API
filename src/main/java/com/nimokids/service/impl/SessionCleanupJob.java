package com.nimokids.service.impl;

import com.nimokids.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically marks inactive STARTED sessions as ABANDONED (master 5.11). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final GameSessionService gameSessionService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void abandonInactiveSessions() {
        int abandoned = gameSessionService.abandonInactiveSessions();
        if (abandoned > 0) {
            log.info("Marked {} inactive session(s) as ABANDONED", abandoned);
        }
    }
}
