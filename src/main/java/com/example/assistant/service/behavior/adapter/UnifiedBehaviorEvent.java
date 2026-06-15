package com.example.assistant.service.behavior.adapter;

import com.example.assistant.model.ActivityType;
import java.time.LocalDateTime;
import java.util.Map;

public record UnifiedBehaviorEvent(
        String userId,
        String platform,
        ActivityType eventType,
        String url,
        String externalId,
        String title,
        String author,
        String contentSnippet,
        LocalDateTime occurredAt,
        Map<String, Object> rawMetadata) {
}
