package com.example.assistant.dto;

import java.util.List;

public record MiniDashboardResponse(
        ProfileResponse profile,
        List<RecommendationResponse> recommendations) {
}
