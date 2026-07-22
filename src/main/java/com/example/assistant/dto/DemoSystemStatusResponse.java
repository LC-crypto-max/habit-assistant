package com.example.assistant.dto;

public record DemoSystemStatusResponse(
        boolean ready,
        String database,
        String databaseVersion,
        String profile,
        String message) {
}
