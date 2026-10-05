package com.nimokids.controller;

import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.TopicResponse;
import com.nimokids.service.TopicService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public ApiResponse<List<TopicResponse>> getTopics() {
        return ApiResponse.success(topicService.getPlayableTopics());
    }

    @GetMapping("/{topicId}")
    public ApiResponse<TopicResponse> getTopic(@PathVariable UUID topicId) {
        return ApiResponse.success(topicService.getTopic(topicId));
    }
}
