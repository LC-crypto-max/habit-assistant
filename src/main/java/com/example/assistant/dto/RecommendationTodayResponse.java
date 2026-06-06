package com.example.assistant.dto;

import com.example.assistant.model.FeedbackType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendationTodayResponse(
        String userId,
        LocalDate date,
        LocalDateTime generatedAt,
        String summary,
        RecommendationRefreshPolicyResponse refreshPolicy,
        List<InterestTermResponse> profileTags,
        List<Item> items) {

    public record Item(
            Long id,
            Long contentId,
            String platform,
            String title,
            String url,
            String author,
            String summary,
            LocalDateTime publishedAt,
            List<String> tags,
            double score,
            String reason,
            FeedbackType feedback,
            List<String> matchedTags,
            Actions actions) {
    }

    public record Actions(
            String click,
            String notInterested,
            String feedback) {
    }
}
