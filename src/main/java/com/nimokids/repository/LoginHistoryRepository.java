package com.nimokids.repository;

import com.nimokids.entity.LoginHistory;
import com.nimokids.entity.enums.LoginOutcome;
import java.net.InetAddress;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    boolean existsByUserIdAndDeviceIdHashAndOutcome(UUID userId, byte[] deviceIdHash, LoginOutcome outcome);

    boolean existsByAdminUserIdAndDeviceIdHashAndOutcome(UUID adminUserId, byte[] deviceIdHash, LoginOutcome outcome);

    boolean existsByUserIdAndIpAndOutcome(UUID userId, InetAddress ip, LoginOutcome outcome);

    boolean existsByAdminUserIdAndIpAndOutcome(UUID adminUserId, InetAddress ip, LoginOutcome outcome);
}
