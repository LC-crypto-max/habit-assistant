package com.example.assistant.dto;

public record AgentWorkerStartRequest(
        Boolean dryRun,
        Integer limit) {
}
