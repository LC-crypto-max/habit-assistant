package com.example.assistant.dto;

public record AgentWorkerStartRequest(
        Boolean dryRun,
        Integer limit,
        Boolean allowAuthenticatedBrowser,
        String agentReachMode,
        Boolean confirmedByUser,
        String taskId) {
}
