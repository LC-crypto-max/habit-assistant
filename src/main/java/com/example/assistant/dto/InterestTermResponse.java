package com.example.assistant.dto;

import java.time.LocalDateTime;

public record InterestTermResponse(
        String term,
        double weight,
        int hitCount,
        LocalDateTime lastSeenAt) {
}
