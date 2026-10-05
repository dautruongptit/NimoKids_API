package com.nimokids.dto.response;

import java.util.List;

public record PlayerProgressResponse(
        int totalGames,
        int totalQuestions,
        int totalCorrect,
        double accuracy,
        List<TopicProgressResponse> topics) {
}
