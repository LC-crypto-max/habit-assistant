package com.example.assistant.controller;

import com.example.assistant.dto.ProfileResponse;
import com.example.assistant.dto.UserSummaryResponse;
import com.example.assistant.service.ProfileService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse currentProfile(@RequestParam(required = false) String userId) {
        return profileService.currentProfile(userId);
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return profileService.users();
    }
}
