package com.example.assistant.dto;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

public record AgentQueryResultRequest(
        Boolean success,
        Boolean ingest,
        String summary,
        String errorMessage,
        List<@Valid AgentInterestItemRequest> items,
        Map<String, Object> metadata) {
}
