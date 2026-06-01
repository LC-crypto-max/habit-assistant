package com.example.assistant.service.collector;

import java.time.LocalDateTime;
import java.util.List;

public record CollectedContent(
        String platform,
        String externalId,
        String title,
        String url,
        String author,
        String summary,
        LocalDateTime publishedAt,
        List<String> tags) {
}
