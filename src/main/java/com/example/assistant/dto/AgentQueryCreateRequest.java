package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentQueryCreateRequest(
        String userId,
        String adapter,
        @NotBlank String platform,
        @NotBlank String intent,
        String url,
        String query) {
}
