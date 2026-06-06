package com.example.assistant.dto;

public record AgentQueryResultResponse(
        AgentQueryTaskResponse task,
        AgentTaskResponse ingestion) {
}
