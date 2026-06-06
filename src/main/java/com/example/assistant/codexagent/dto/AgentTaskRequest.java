package com.example.assistant.codexagent.dto;

import com.example.assistant.codexagent.policy.DataAccessScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record AgentTaskRequest(
        @NotBlank String taskId,
        @NotBlank String userId,
        @NotBlank String action,
        @NotNull LocalDate date,
        List<DataAccessScope> sources,
        Integer maxRecords,
        RecommendationOptions recommendation) {

    public record RecommendationOptions(
            Integer topK,
            String language,
            List<String> categories) {
    }
}
