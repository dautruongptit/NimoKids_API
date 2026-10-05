package com.nimokids.repository;

import com.nimokids.entity.GameMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameModeRepository extends JpaRepository<GameMode, UUID> {

    Optional<GameMode> findByCode(String code);

    List<GameMode> findByActiveTrueOrderByNameAsc();
}
