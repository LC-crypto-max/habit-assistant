package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendationSearchRequest(
        String userId,
        @NotBlank String keyword,
        String platform,
        boolean refresh) {
}
