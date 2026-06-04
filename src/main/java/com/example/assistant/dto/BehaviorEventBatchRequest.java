package com.example.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BehaviorEventBatchRequest(
        @NotEmpty List<@Valid BehaviorEventRequest> events) {
}
