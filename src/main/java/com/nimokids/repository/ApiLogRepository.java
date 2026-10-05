package com.nimokids.repository;

import com.nimokids.entity.ApiLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiLogRepository extends JpaRepository<ApiLog, UUID> {

    Optional<ApiLog> findByRequestId(UUID requestId);
}
