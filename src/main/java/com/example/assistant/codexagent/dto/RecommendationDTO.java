package com.example.assistant.codexagent.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendationDTO(
        String userId,
        LocalDate date,
        List<RecommendationItem> recommendations) {

    public record RecommendationItem(
            String title,
            String reason,
            String category,
            List<String> keywords,
            String suggestedPlatform,
            String query) {
    }
}
