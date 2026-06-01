package com.example.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContentItemBackupRecord(
        Long originalId,
        String platform,
        String externalId,
        String title,
        String url,
        String author,
        String summary,
        LocalDateTime publishedAt,
        LocalDateTime collectedAt,
        String contentHash,
        List<String> tags) {
}
