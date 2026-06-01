package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public record VisitRecordRequest(
        String userId,
        @NotBlank String title,
        @NotBlank String url,
        String platform,
        LocalDateTime visitedAt,
        List<String> tags) {
}
