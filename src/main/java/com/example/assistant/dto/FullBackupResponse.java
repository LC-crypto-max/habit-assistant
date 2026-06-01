package com.example.assistant.dto;

import java.util.List;

public record FullBackupResponse(
        ActivityBackupResponse activities,
        int contentCount,
        List<ContentItemBackupRecord> contents,
        int recommendationCount,
        List<RecommendationBackupRecord> recommendations) {
}
