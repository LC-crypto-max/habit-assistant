package com.example.assistant.config;

import com.example.assistant.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AssistantProperties properties;
    private final AuthService authService;

    public AuthInterceptor(AssistantProperties properties, AuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.getAuth().isEnabled() || isPublic(request.getRequestURI())) {
            return true;
        }
        if (authService.currentUserId(request.getSession(false)) != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"请先登录。\"}");
        return false;
    }

    private boolean isPublic(String uri) {
        return uri == null
                || uri.equals("/")
                || uri.startsWith("/api/auth/")
                || uri.startsWith("/actuator/")
                || uri.startsWith("/h2-console")
                || !uri.startsWith("/api/");
    }
}
