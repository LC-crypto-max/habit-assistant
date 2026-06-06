package com.example.assistant.codexagent.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeAuthorizationRequest(
        @NotBlank String userId,
        @NotBlank String authorizationId) {
}
