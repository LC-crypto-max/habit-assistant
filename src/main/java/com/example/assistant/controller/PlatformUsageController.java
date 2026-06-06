package com.example.assistant.controller;

import com.example.assistant.dto.PlatformUsageSummaryResponse;
import com.example.assistant.service.PlatformUsageSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platforms")
public class PlatformUsageController {

    private final PlatformUsageSummaryService platformUsageSummaryService;

    public PlatformUsageController(PlatformUsageSummaryService platformUsageSummaryService) {
        this.platformUsageSummaryService = platformUsageSummaryService;
    }

    @GetMapping("/xiaohongshu/usage-summary")
    public PlatformUsageSummaryResponse xiaohongshuUsageSummary(@RequestParam(required = false) String userId) {
        return platformUsageSummaryService.xiaohongshu(userId);
    }
}
