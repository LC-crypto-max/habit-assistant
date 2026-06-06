package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.AuthLoginRequest;
import com.example.assistant.dto.AuthUserResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public static final String SESSION_USER_ID = "HABIT_USER_ID";
    public static final String SESSION_ADMIN = "HABIT_ADMIN";

    private final AssistantProperties properties;

    public AuthService(AssistantProperties properties) {
        this.properties = properties;
    }

    public AuthUserResponse login(AuthLoginRequest request, HttpSession session) {
        AssistantProperties.AuthUser matched = properties.getAuth().getUsers().stream()
                .filter(user -> Objects.equals(user.getUserId(), request.userId()))
                .filter(user -> Objects.equals(user.getPassword(), request.password()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AUTH_INVALID_CREDENTIALS"));
        session.setAttribute(SESSION_USER_ID, matched.getUserId());
        session.setAttribute(SESSION_ADMIN, matched.isAdmin());
        return new AuthUserResponse(true, matched.getUserId(), matched.isAdmin());
    }

    public AuthUserResponse me(HttpSession session) {
        String userId = currentUserId(session);
        if (userId == null) {
            return new AuthUserResponse(false, null, false);
        }
        return new AuthUserResponse(true, userId, isAdmin(session));
    }

    public AuthUserResponse logout(HttpSession session) {
        session.invalidate();
        return new AuthUserResponse(false, null, false);
    }

    public String currentUserId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER_ID);
        return value == null ? null : value.toString();
    }

    public boolean isAdmin(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(SESSION_ADMIN);
        return Boolean.TRUE.equals(value);
    }
}
