package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import java.time.LocalDateTime;
import java.util.List;

public record ActivityResponse(
        Long id,
        ActivityType type,
        String platform,
        String title,
        String url,
        String text,
        LocalDateTime occurredAt,
        List<String> tags,
        String confidence,
        String dataLevel,
        String source,
        String detectionReason,
        String matchedKeyword) {
}
