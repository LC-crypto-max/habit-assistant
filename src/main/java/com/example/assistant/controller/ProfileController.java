package com.example.assistant.controller;

import com.example.assistant.dto.DailyProfileResponse;
import com.example.assistant.dto.ProfileResponse;
import com.example.assistant.dto.ProfileV2Response;
import com.example.assistant.dto.UserSummaryResponse;
import com.example.assistant.service.ProfileService;
import com.example.assistant.service.UserProfileBuilder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final UserProfileBuilder userProfileBuilder;

    public ProfileController(ProfileService profileService, UserProfileBuilder userProfileBuilder) {
        this.profileService = profileService;
        this.userProfileBuilder = userProfileBuilder;
    }

    @GetMapping
    public ProfileResponse currentProfile(@RequestParam(required = false) String userId) {
        return profileService.currentProfile(userId);
    }

    @GetMapping("/current")
    public ProfileV2Response currentDailyProfile(@RequestParam(required = false) String userId) {
        return userProfileBuilder.build(userId);
    }

    @GetMapping("/daily")
    public DailyProfileResponse dailyProfile(@RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return profileService.dailyProfile(userId, refresh);
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return profileService.users();
    }
}
