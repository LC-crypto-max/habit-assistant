package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public record ActivityRequest(
        String userId,
        @NotNull ActivityType type,
        String platform,
        String title,
        String url,
        @NotBlank String text,
        LocalDateTime occurredAt,
        List<String> tags) {
}
