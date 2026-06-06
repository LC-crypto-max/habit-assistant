package com.example.assistant.dto;

import java.util.List;
import java.util.Map;

public record AgentTaskResponse(
        String taskId,
        String adapter,
        String status,
        int received,
        int imported,
        int skipped,
        List<BehaviorEventRequest> events,
        List<ActivityResponse> activities,
        Map<String, Object> metadata) {
}
