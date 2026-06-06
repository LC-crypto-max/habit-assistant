package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class UserContext {

    private final AssistantProperties properties;

    public UserContext(AssistantProperties properties) {
        this.properties = properties;
    }

    public String resolve(String userId) {
        if (properties.getAuth().isEnabled()) {
            String sessionUserId = sessionUserId();
            if (sessionUserId != null && !sessionUserId.isBlank()) {
                return sessionUserId;
            }
        }
        if (userId == null || userId.isBlank()) {
            return properties.getUserId();
        }
        return userId.trim();
    }

    public String currentOrDefault() {
        return resolve(null);
    }

    private String sessionUserId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(AuthService.SESSION_USER_ID);
        return value == null ? null : value.toString();
    }
}
