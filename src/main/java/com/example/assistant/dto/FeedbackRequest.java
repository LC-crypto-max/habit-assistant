package com.example.assistant.dto;

import com.example.assistant.model.FeedbackType;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(@NotNull FeedbackType feedback) {
}
