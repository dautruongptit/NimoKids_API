package com.nimokids.service.impl;

import com.nimokids.dto.response.GameModeResponse;
import com.nimokids.mapper.GameMapper;
import com.nimokids.repository.GameModeRepository;
import com.nimokids.service.GameModeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameModeServiceImpl implements GameModeService {

    private final GameModeRepository gameModeRepository;
    private final GameMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<GameModeResponse> getActiveGameModes() {
        return gameModeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(mapper::toGameModeResponse)
                .toList();
    }
}
