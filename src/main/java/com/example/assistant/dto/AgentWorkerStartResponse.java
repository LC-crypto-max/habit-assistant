package com.example.assistant.dto;

import java.util.List;

public record AgentWorkerStartResponse(
        boolean started,
        String mode,
        String message,
        List<String> command,
        String workingDirectory) {
}
