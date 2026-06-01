package com.example.assistant.dto;

public record SubmitResponse<T>(
        boolean success,
        String message,
        T data) {
}
