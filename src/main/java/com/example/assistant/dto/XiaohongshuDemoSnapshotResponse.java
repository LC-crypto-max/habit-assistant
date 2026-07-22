package com.example.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public record XiaohongshuDemoSnapshotResponse(
        String userId,
        LocalDateTime generatedAt,
        Readiness readiness,
        boolean degraded,
        List<ComponentStatus> components,
        PlatformUsageSummaryResponse usage,
        ProfileV2Response profile,
        RecommendationTodaySummaryResponse recommendations,
        List<VisitAnalysis> visits) {

    public record Readiness(
            boolean ready,
            boolean visitCaptured,
            boolean agentReachLive,
            boolean aiAnalyzed,
            boolean profileReady,
            boolean recommendationsReady,
            List<String> hints) {
    }

    public record ComponentStatus(
            String component,
            String status,
            String message) {
    }

    public record VisitAnalysis(
            Long id,
            String title,
            String url,
            String author,
            String summary,
            List<String> tags,
            LocalDateTime occurredAt,
            String confidence,
            String dataLevel,
            String source,
            String adapterMode,
            String agentReachStatus,
            String agentReachRoute,
            String agentReachBackend,
            String llmStatus,
            int llmLatencyMs,
            String interestCategory,
            String intent) {
    }
}
