package com.example.assistant.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyProfileResponse(
        String userId,
        LocalDate profileDate,
        LocalDateTime generatedAt,
        String summary,
        ProfileWindowResponse last7Days,
        ProfileWindowResponse last30Days,
        List<InterestTermResponse> topTags,
        List<ActivityResponse> recentActivities,
        boolean cached) {

    public DailyProfileResponse withCached(boolean cached) {
        return new DailyProfileResponse(
                userId,
                profileDate,
                generatedAt,
                summary,
                last7Days,
                last30Days,
                topTags,
                recentActivities,
                cached);
    }
}
