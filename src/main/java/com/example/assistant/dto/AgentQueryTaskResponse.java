package com.example.assistant.dto;

import com.example.assistant.model.AgentQueryStatus;
import java.time.LocalDateTime;

public record AgentQueryTaskResponse(
        String taskId,
        String userId,
        String adapter,
        String platform,
        String intent,
        String url,
        String query,
        String prompt,
        AgentQueryStatus status,
        LocalDateTime createdAt,
        LocalDateTime claimedAt,
        LocalDateTime finishedAt,
        String errorMessage) {
}
