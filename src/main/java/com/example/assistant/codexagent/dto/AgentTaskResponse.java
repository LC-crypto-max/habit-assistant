package com.example.assistant.codexagent.dto;

import java.util.List;

public record AgentTaskResponse(
        String status,
        String taskId,
        int eventsCount,
        String profilePath,
        String recommendationsPath,
        List<String> warnings,
        String reason,
        String safeAlternative) {
}
