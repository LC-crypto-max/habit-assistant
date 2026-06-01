package com.example.assistant.controller;

import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.service.ActivityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/activities")
    public ActivityResponse recordActivity(@Valid @RequestBody ActivityRequest request) {
        return activityService.record(request);
    }

    @PostMapping("/search-terms")
    public ActivityResponse recordSearchTerm(@Valid @RequestBody SearchTermRequest request) {
        return activityService.recordSearchTerm(request);
    }

    @GetMapping("/activities/recent")
    public List<ActivityResponse> recentActivities(@RequestParam(required = false) String userId) {
        return activityService.recent(userId);
    }
}
