package com.nimokids.dto.response;

import com.nimokids.entity.enums.StickerRarity;
import java.time.Instant;

public record StickerResponse(String code, String name, String image, StickerRarity rarity, Instant earnedAt) {
}
