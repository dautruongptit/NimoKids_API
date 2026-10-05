package com.nimokids.service;

import com.nimokids.dto.response.TopicResponse;
import java.util.List;
import java.util.UUID;

public interface TopicService {

    /** Active topics that have at least 5 playable questions (business-rules BR-001, section 30). */
    List<TopicResponse> getPlayableTopics();

    TopicResponse getTopic(UUID topicId);
}
