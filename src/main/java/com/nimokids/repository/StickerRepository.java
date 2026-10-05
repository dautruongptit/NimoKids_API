package com.nimokids.repository;

import com.nimokids.entity.Sticker;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StickerRepository extends JpaRepository<Sticker, UUID> {

    Optional<Sticker> findByCode(String code);
}
