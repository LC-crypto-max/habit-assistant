package com.example.assistant.dto;

import java.util.List;

public record RecommendationSearchResponse(
        String userId,
        String keyword,
        ProfileResponse profile,
        List<RecommendationResponse> recommendations,
        RecommendationRefreshPolicyResponse refreshPolicy) {
}
