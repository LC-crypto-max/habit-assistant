package com.example.assistant.dto;

import com.example.assistant.model.ActivityType;
import com.example.assistant.support.IsoLocalDateTimeDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AgentInterestItemRequest(
        @NotBlank String platform,
        @JsonAlias("eventType") ActivityType type,
        String externalId,
        String title,
        String url,
        String author,
        String summary,
        String text,
        @JsonDeserialize(using = IsoLocalDateTimeDeserializer.class) LocalDateTime occurredAt,
        List<String> tags,
        String confidence,
        String dataLevel,
        String detectionReason,
        String matchedKeyword,
        List<String> interestTags,
        List<String> interestLabels,
        List<String> recommendationHints,
        String contentType,
        @JsonAlias("interestCategory") String contentCategory,
        String intent,
        String summaryForProfile,
        Map<String, Object> rawEvidence) {
}
