package com.example.assistant.codexagent.dto;

import java.time.OffsetDateTime;

public record BrowserVisitEventDTO(
        String url,
        String title,
        OffsetDateTime visitTime,
        int visitCount) {
}
