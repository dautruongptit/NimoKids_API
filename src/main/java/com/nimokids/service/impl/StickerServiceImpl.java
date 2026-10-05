package com.nimokids.service.impl;

import com.nimokids.dto.response.StickerResponse;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.QuestionSticker;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.Sticker;
import com.nimokids.mapper.GameMapper;
import com.nimokids.repository.PlayerStickerRepository;
import com.nimokids.repository.QuestionStickerRepository;
import com.nimokids.repository.StickerRepository;
import com.nimokids.service.PlayerService;
import com.nimokids.service.StickerService;
import com.nimokids.util.GameConstants;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StickerServiceImpl implements StickerService {

    private final StickerRepository stickerRepository;
    private final PlayerStickerRepository playerStickerRepository;
    private final QuestionStickerRepository questionStickerRepository;
    private final PlayerService playerService;
    private final GameMapper mapper;

    @Override
    @Transactional
    public void awardCompletionStickers(GameSession session, AnonymousPlayer player, Instant now) {
        // BR-033: FIRST_GAME on the first completed game. The unique constraint makes later attempts no-ops.
        // BR-034: PERFECT_SCORE when every question was answered correctly.
        Set<String> codes = new LinkedHashSet<>();
        codes.add(GameConstants.STICKER_FIRST_GAME);
        if (session.getScore().intValue() == session.getTotalQuestions().intValue()) {
            codes.add(GameConstants.STICKER_PERFECT_SCORE);
        }
        for (String code : codes) {
            stickerRepository.findByCode(code)
                    .filter(Sticker::isActive)
                    .ifPresent(sticker -> award(player, sticker, session, now));
        }

        // Rewards attached to individual questions, granted when the question ended with the configured result.
        Map<UUID, SessionQuestion> byQuestionId = session.getSessionQuestions().stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), Function.identity()));
        for (QuestionSticker reward : questionStickerRepository.findByQuestionIdIn(byQuestionId.keySet())) {
            SessionQuestion answered = byQuestionId.get(reward.getQuestion().getId());
            boolean conditionMet = answered.getResult() != null
                    && answered.getResult().name().equalsIgnoreCase(reward.getResultCondition());
            if (conditionMet && reward.getSticker().isActive()) {
                award(player, reward.getSticker(), session, now);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StickerResponse> getPlayerStickers(UUID anonymousId) {
        return playerService.find(anonymousId)
                .map(player -> playerStickerRepository.findByPlayerIdOrderByEarnedAtDesc(player.getId()).stream()
                        .map(owned -> mapper.toStickerResponse(owned.getSticker(), owned.getEarnedAt()))
                        .toList())
                .orElse(List.of());
    }

    private void award(AnonymousPlayer player, Sticker sticker, GameSession session, Instant now) {
        playerStickerRepository.insertIfAbsent(player.getId(), sticker.getId(), session.getId(), now);
    }
}
