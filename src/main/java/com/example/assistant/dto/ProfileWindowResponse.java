package com.example.assistant.dto;

import java.util.List;
import java.util.Map;

public record ProfileWindowResponse(
        int days,
        long activityCount,
        Map<String, Long> typeCounts,
        Map<String, Long> platformCounts,
        List<InterestTermResponse> tags) {
}
