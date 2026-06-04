package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public record DataSourceEventRequest(
        String userId,
        @NotBlank String platform,
        ActivityType type,
        String externalId,
        String title,
        String url,
        String author,
        String summary,
        String text,
        LocalDateTime occurredAt,
        List<String> tags) {
}
