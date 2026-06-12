package com.example.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileV2Response(
        String userId,
        String profileVersion,
        LocalDateTime generatedAt,
        List<TopInterest> topInterests,
        List<PlatformPreference> platformPreferences,
        String summary,
        List<Evidence> evidence) {

    public record TopInterest(
            String name,
            double score,
            int evidenceCount,
            String confidence) {
    }

    public record PlatformPreference(
            String platform,
            double score,
            int evidenceCount,
            String bestDataLevel) {
    }

    public record Evidence(
            String eventId,
            String title,
            String url,
            String source,
            String confidence) {
    }
}
