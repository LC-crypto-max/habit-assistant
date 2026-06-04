package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import java.time.LocalDateTime;
import java.util.List;

public record BehaviorEventMessage(
        Long activityId,
        String userId,
        ActivityType type,
        String platform,
        String source,
        String externalId,
        String title,
        String url,
        String text,
        LocalDateTime occurredAt,
        List<String> tags,
        LocalDateTime publishedAt) {
}
