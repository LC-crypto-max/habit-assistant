package com.example.assistant.dto;

import java.util.Map;

public record ResetDataResponse(
        boolean success,
        String backupPath,
        Map<String, Long> deletedCounts,
        String message) {
}
