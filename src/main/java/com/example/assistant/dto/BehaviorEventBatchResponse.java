package com.example.assistant.dto;

import java.util.List;

public record BehaviorEventBatchResponse(
        int imported,
        int skipped,
        int messagesPublished,
        List<ActivityResponse> activities) {
}
