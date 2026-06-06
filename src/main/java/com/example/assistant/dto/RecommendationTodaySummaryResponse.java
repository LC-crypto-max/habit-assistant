package com.example.assistant.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendationTodaySummaryResponse(
        String userId,
        LocalDate date,
        Integer refreshIntervalHours,
        long behaviorCount24h,
        List<RecommendationResponse> recommendations,
        String message,
        String status) {
}
