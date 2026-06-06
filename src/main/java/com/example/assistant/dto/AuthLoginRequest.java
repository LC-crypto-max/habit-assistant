package com.example.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank String userId,
        @NotBlank String password) {
}
