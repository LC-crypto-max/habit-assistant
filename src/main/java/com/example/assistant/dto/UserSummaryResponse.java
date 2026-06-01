package com.example.assistant.dto;

public record UserSummaryResponse(
        String userId,
        long activityCount) {
}
