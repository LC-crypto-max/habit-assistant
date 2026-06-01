package com.example.assistant.dto;

import java.time.LocalDateTime;

public record RecommendationRefreshPolicyResponse(
        String userId,
        long recentActivityCount,
        int activityWindowHours,
        int refreshHours,
        LocalDateTime latestRecommendationAt,
        LocalDateTime expiresAt,
        boolean expired) {
}
