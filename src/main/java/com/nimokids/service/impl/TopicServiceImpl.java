package com.nimokids.service.impl;

import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.dto.response.TopicResponse;
import com.nimokids.entity.Topic;
import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.exception.TopicNotPlayableException;
import com.nimokids.mapper.GameMapper;
import com.nimokids.repository.GameQuestionRepository;
import com.nimokids.repository.TopicRepository;
import com.nimokids.service.TopicService;
import com.nimokids.util.GameConstants;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final GameQuestionRepository questionRepository;
    private final GameMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getPlayableTopics(LanguageMode languageMode) {
        Set<UUID> playableTopicIds = Set.copyOf(
                questionRepository.findPlayableTopicIds(GameConstants.MIN_PLAYABLE_QUESTIONS_PER_TOPIC, languageMode));
        log.debug("Playable topics for mode {}: {} topic id(s) with enough questions", languageMode, playableTopicIds.size());
        return topicRepository.findActiveSupportingMode(languageMode.name()).stream()
                .filter(topic -> playableTopicIds.contains(topic.getId()))
                .map(topic -> mapper.toTopicResponse(topic, languageMode))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TopicResponse getTopic(UUID topicId, LanguageMode languageMode) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
        if (!topic.isActive() || !topic.supports(languageMode)) {
            throw new TopicNotPlayableException();
        }
        return mapper.toTopicResponse(topic, languageMode);
    }
}
