package com.example.assistant.dto;

public record ImportFullBackupResponse(
        int activitiesImported,
        int contentsImported,
        int recommendationsImported) {
}
