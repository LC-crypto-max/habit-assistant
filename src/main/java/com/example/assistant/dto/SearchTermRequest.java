package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record SearchTermRequest(
        String userId,
        @NotBlank String keyword,
        String platform,
        LocalDateTime occurredAt) {
}
