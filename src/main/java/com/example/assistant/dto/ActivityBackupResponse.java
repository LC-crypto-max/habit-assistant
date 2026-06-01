package com.example.assistant.dto;

import java.util.List;

public record ActivityBackupResponse(
        int count,
        List<ActivityRequest> activities) {
}
