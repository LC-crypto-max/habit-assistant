package com.example.assistant.dto;

public record FeishuPushRequest(
        String userId,
        String webhookUrl) {
}
