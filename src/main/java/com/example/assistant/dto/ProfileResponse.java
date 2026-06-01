package com.example.assistant.dto;

import java.util.List;

public record ProfileResponse(
        String userId,
        List<InterestTermResponse> topTerms,
        List<ActivityResponse> recentActivities) {
}
