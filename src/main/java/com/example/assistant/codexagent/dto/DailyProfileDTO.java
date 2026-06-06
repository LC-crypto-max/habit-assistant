package com.example.assistant.codexagent.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyProfileDTO(
        String userId,
        LocalDate date,
        List<TagScore> topTags,
        Map<String, Double> platformDistribution,
        String interestSummary,
        List<String> recommendationIntent) {

    public record TagScore(String tag, double score) {
    }
}
