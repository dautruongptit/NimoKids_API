package com.nimokids.service;

import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.dto.response.TopicResponse;
import java.util.List;
import java.util.UUID;

public interface TopicService {

    /** Active topics that have at least 5 playable questions (business-rules BR-001, section 30). */
    List<TopicResponse> getPlayableTopics(LanguageMode languageMode);

    TopicResponse getTopic(UUID topicId, LanguageMode languageMode);

    default List<TopicResponse> getPlayableTopics() {
        return getPlayableTopics(LanguageMode.EN);
    }

    default TopicResponse getTopic(UUID topicId) {
        return getTopic(topicId, LanguageMode.EN);
    }
}
