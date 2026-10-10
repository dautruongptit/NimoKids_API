package com.nimokids.service.auth;

import com.nimokids.entity.LoginHistory;
import com.nimokids.entity.enums.AuthMethod;
import com.nimokids.entity.enums.LoginOutcome;
import com.nimokids.repository.LoginHistoryRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the append-only login history, in its own transaction so failed attempts are kept. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    /** What the writer learned: whether this device / IP had never signed in successfully before. */
    public record Recorded(UUID id, boolean newDevice, boolean newIp) {
    }

    private final LoginHistoryRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recorded record(UUID userId, UUID adminUserId, UUID sessionId, AuthMethod method, LoginOutcome outcome,
                           String failureCode, ClientContext ctx, String identifierHint) {
        boolean newDevice = false;
        boolean newIp = false;
        if (outcome == LoginOutcome.SUCCESS) {
            if (ctx.deviceIdHash() != null) {
                newDevice = !(userId != null
                        ? repository.existsByUserIdAndDeviceIdHashAndOutcome(userId, ctx.deviceIdHash(), LoginOutcome.SUCCESS)
                        : repository.existsByAdminUserIdAndDeviceIdHashAndOutcome(adminUserId, ctx.deviceIdHash(), LoginOutcome.SUCCESS));
            }
            if (ctx.ip() != null) {
                newIp = !(userId != null
                        ? repository.existsByUserIdAndIpAndOutcome(userId, ctx.ip(), LoginOutcome.SUCCESS)
                        : repository.existsByAdminUserIdAndIpAndOutcome(adminUserId, ctx.ip(), LoginOutcome.SUCCESS));
            }
        }
        LoginHistory saved = repository.save(LoginHistory.builder()
                .occurredAt(clock.instant())
                .userId(userId)
                .adminUserId(adminUserId)
                .sessionId(sessionId)
                .method(method)
                .outcome(outcome)
                .failureCode(failureCode)
                .ip(ctx.ip())
                .userAgent(ctx.userAgent())
                .browser(ctx.browser())
                .os(ctx.os())
                .deviceType(ctx.deviceType())
                .deviceIdHash(ctx.deviceIdHash())
                .newDevice(newDevice)
                .newIp(newIp)
                .identifierHint(identifierHint)
                .requestId(ctx.requestId())
                .build());
        return new Recorded(saved.getId(), newDevice, newIp);
    }
}
