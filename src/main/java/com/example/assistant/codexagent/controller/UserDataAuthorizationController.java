package com.example.assistant.codexagent.controller;

import com.example.assistant.codexagent.dto.AuthorizationRequest;
import com.example.assistant.codexagent.dto.AuthorizationResponse;
import com.example.assistant.codexagent.dto.RevokeAuthorizationRequest;
import com.example.assistant.codexagent.service.UserDataAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-data-authorization")
public class UserDataAuthorizationController {

    private final UserDataAuthorizationService authorizationService;

    public UserDataAuthorizationController(UserDataAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @PostMapping("/grant")
    public AuthorizationResponse grant(@Valid @RequestBody AuthorizationRequest request) {
        return authorizationService.grant(request);
    }

    @GetMapping("/{userId}")
    public AuthorizationResponse current(@PathVariable String userId) {
        return authorizationService.current(userId);
    }

    @PostMapping("/revoke")
    public AuthorizationResponse revoke(@Valid @RequestBody RevokeAuthorizationRequest request) {
        return authorizationService.revoke(request);
    }
}
