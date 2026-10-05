package com.nimokids.controller;

import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.GameModeResponse;
import com.nimokids.service.GameModeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-modes")
@RequiredArgsConstructor
public class GameModeController {

    private final GameModeService gameModeService;

    @GetMapping
    public ApiResponse<List<GameModeResponse>> getGameModes() {
        return ApiResponse.success(gameModeService.getActiveGameModes());
    }
}
