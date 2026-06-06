package com.example.assistant.controller;

import com.example.assistant.dto.AuthLoginRequest;
import com.example.assistant.dto.AuthUserResponse;
import com.example.assistant.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody AuthLoginRequest request, HttpSession session) {
        return authService.login(request, session);
    }

    @PostMapping("/logout")
    public AuthUserResponse logout(HttpSession session) {
        return authService.logout(session);
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpSession session) {
        return authService.me(session);
    }
}
