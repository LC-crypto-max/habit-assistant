package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HabitSubmissionRequest(
        @NotBlank @Size(max = 40) String nickname,
        @NotBlank @Size(max = 80) String habitName,
        @NotBlank @Size(max = 500) String content,
        @NotNull LocalDate recordDate,
        @Size(max = 300) String remark) {
}
