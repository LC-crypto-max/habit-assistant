package com.example.assistant.dto;

import java.util.List;

public record DataSourceBatchResponse(
        int imported,
        int skipped,
        List<ActivityResponse> activities) {
}
