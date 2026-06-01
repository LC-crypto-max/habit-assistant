package com.example.assistant.dto;

import java.util.List;

public record ImportResultResponse(
        int imported,
        int skipped,
        List<ActivityResponse> activities) {
}
