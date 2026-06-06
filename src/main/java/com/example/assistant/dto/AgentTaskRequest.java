package com.example.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record AgentTaskRequest(
        String userId,
        @NotBlank String taskId,
        String adapter,
        String intent,
        String platform,
        String title,
        String url,
        String query,
        String summary,
        String text,
        Boolean ingest,
        List<@Valid AgentInterestItemRequest> items,
        Map<String, Object> metadata) {
}
