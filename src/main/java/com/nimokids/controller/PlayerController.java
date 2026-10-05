package com.nimokids.controller;

import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.StickerResponse;
import com.nimokids.service.StickerService;
import com.nimokids.validation.AnonymousId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "me" is resolved from X-Anonymous-Id, so the anonymous id never appears in a URL
 * (business-rules BR-029).
 */
@RestController
@RequestMapping("/api/v1/players/me")
@RequiredArgsConstructor
public class PlayerController {

    private final StickerService stickerService;

    @GetMapping("/stickers")
    public ApiResponse<List<StickerResponse>> getStickers(@AnonymousId UUID anonymousId) {
        return ApiResponse.success(stickerService.getPlayerStickers(anonymousId));
    }
}
