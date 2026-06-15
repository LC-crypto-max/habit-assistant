package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import com.example.assistant.support.IsoLocalDateTimeDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BehaviorEventRequest(
        @NotBlank String userId,
        @NotBlank String platform,
        @JsonAlias("eventType") @NotNull(message = "eventType is required") ActivityType type,
        String source,
        String externalId,
        String title,
        String url,
        String author,
        @JsonAlias("contentSnippet") String summary,
        String text,
        @JsonAlias("createdAt") @JsonDeserialize(using = IsoLocalDateTimeDeserializer.class) LocalDateTime occurredAt,
        List<String> tags,
        String confidence,
        String dataLevel,
        String detectionReason,
        String matchedKeyword,
        List<String> interestTags,
        String contentType,
        @JsonAlias("interestCategory") String contentCategory,
        String intent,
        String summaryForProfile,
        List<String> recommendationHints,
        @JsonAlias("rawMetadata") Map<String, Object> rawEvidence) {
}
