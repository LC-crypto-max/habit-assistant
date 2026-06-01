package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import org.springframework.stereotype.Component;

@Component
public class UserContext {

    private final AssistantProperties properties;

    public UserContext(AssistantProperties properties) {
        this.properties = properties;
    }

    public String resolve(String userId) {
        if (userId == null || userId.isBlank()) {
            return properties.getUserId();
        }
        return userId.trim();
    }
}
