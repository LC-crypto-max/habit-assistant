package com.example.assistant.codexagent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public record ClientAppUsageRequest(
        @NotBlank String userId,
        LocalDate date,
        List<@Valid AppUsageEventDTO> events) {
}
