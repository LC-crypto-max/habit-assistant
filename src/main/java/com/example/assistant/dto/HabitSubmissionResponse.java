package com.example.assistant.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HabitSubmissionResponse(
        Long id,
        String nickname,
        String habitName,
        String content,
        LocalDate recordDate,
        String remark,
        LocalDateTime createdAt) {
}
