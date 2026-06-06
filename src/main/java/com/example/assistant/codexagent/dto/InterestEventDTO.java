package com.example.assistant.codexagent.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record InterestEventDTO(
        String eventId,
        String userId,
        String source,
        String platform,
        String eventType,
        String title,
        String url,
        String author,
        List<String> tags,
        String summary,
        OffsetDateTime timestamp,
        double weight,
        Map<String, Object> metadata) {
}
