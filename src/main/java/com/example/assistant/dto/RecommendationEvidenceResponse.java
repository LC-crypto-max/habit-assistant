package com.example.assistant.dto;

public record RecommendationEvidenceResponse(
        String eventId,
        String title,
        String url,
        String source,
        String confidence) {
}
