package com.example.assistant.dto;

import com.example.assistant.model.FeedbackType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecommendationBackupRecord(
        String userId,
        Long contentOriginalId,
        LocalDate recommendationDate,
        double score,
        String reason,
        FeedbackType feedback,
        LocalDateTime createdAt) {
}
