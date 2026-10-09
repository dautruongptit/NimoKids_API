package com.nimokids.controller;

import org.springframework.web.bind.annotation.RequestParam;
import com.nimokids.entity.enums.LanguageMode;
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
    public ApiResponse<List<TopicResponse>> getTopics(@RequestParam(required = false) LanguageMode languageMode) {
        return ApiResponse.success(topicService.getPlayableTopics(languageMode != null ? languageMode : LanguageMode.EN));
    }

    @GetMapping("/{topicId}")
    public ApiResponse<TopicResponse> getTopic(@PathVariable UUID topicId,
                                               @RequestParam(required = false) LanguageMode languageMode) {
        return ApiResponse.success(topicService.getTopic(topicId, languageMode != null ? languageMode : LanguageMode.EN));
    }
}
