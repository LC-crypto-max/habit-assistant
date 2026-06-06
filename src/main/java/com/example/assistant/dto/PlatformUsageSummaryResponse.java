package com.example.assistant.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PlatformUsageSummaryResponse(
        String platform,
        String userId,
        LocalDate date,
        String summary,
        String overallConfidence,
        List<Signal> windowsAppSignals,
        List<Signal> browserHistorySignals,
        List<Signal> pageContentSignals,
        String recommendationHint) {

    public record Signal(
            String title,
            String url,
            String author,
            String summary,
            String source,
            String confidence,
            String dataLevel,
            String detectionReason,
            String matchedKeyword,
            LocalDateTime occurredAt) {
    }
}
