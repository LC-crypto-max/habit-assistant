package com.example.assistant.codexagent.dto;

import java.time.OffsetDateTime;

public record AppUsageEventDTO(
        String packageName,
        String appName,
        OffsetDateTime firstTimeUsed,
        OffsetDateTime lastTimeUsed,
        long totalTimeInForegroundSeconds,
        int launchCount) {
}
