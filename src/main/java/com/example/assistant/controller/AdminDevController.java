package com.example.assistant.controller;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ResetDataResponse;
import com.example.assistant.service.AdminDataResetService;
import com.example.assistant.service.AuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dev")
public class AdminDevController {

    private final AdminDataResetService resetService;
    private final Environment environment;
    private final AssistantProperties properties;
    private final AuthService authService;

    public AdminDevController(AdminDataResetService resetService, Environment environment,
            AssistantProperties properties, AuthService authService) {
        this.resetService = resetService;
        this.environment = environment;
        this.properties = properties;
        this.authService = authService;
    }

    @PostMapping("/reset-data")
    public ResetDataResponse resetData(HttpSession session) {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
        boolean admin = !properties.getAuth().isEnabled() || authService.isAdmin(session);
        if (!devProfile && !admin) {
            throw new IllegalArgumentException("RESET_DATA_FORBIDDEN");
        }
        return resetService.resetDevData();
    }
}
