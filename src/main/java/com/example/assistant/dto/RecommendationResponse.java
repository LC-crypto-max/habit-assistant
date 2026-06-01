package com.example.assistant.dto;

import com.example.assistant.model.FeedbackType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(
        Long id,
        LocalDate date,
        double score,
        String reason,
        FeedbackType feedback,
        Content content) {

    public record Content(
            Long id,
            String platform,
            String title,
            String url,
            String author,
            String summary,
            LocalDateTime publishedAt,
            List<String> tags) {
    }
}
