package com.nimokids.service;

import com.nimokids.dto.response.GameModeResponse;
import java.util.List;

public interface GameModeService {

    List<GameModeResponse> getActiveGameModes();
}
