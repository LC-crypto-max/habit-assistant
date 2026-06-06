package com.example.assistant.dto;

public record AuthUserResponse(
        boolean authenticated,
        String userId,
        boolean admin) {
}
