package com.example.assistant.controller;

import com.example.assistant.dto.ActivityBackupResponse;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.FeishuPushRequest;
import com.example.assistant.dto.FeishuPushResponse;
import com.example.assistant.dto.FullBackupResponse;
import com.example.assistant.dto.ImportBackupResponse;
import com.example.assistant.dto.ImportFullBackupResponse;
import com.example.assistant.dto.MiniDashboardResponse;
import com.example.assistant.dto.RecommendationResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.dto.VisitRecordRequest;
import com.example.assistant.service.ActivityService;
import com.example.assistant.service.DataMigrationService;
import com.example.assistant.service.FeishuBotService;
import com.example.assistant.service.ProfileService;
import com.example.assistant.service.RecommendationService;
import com.example.assistant.service.VisitRecordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IntegrationController {

    private final FeishuBotService feishuBotService;
    private final ProfileService profileService;
    private final RecommendationService recommendationService;
    private final ActivityService activityService;
    private final VisitRecordService visitRecordService;
    private final DataMigrationService dataMigrationService;

    public IntegrationController(FeishuBotService feishuBotService, ProfileService profileService,
            RecommendationService recommendationService, ActivityService activityService,
            VisitRecordService visitRecordService, DataMigrationService dataMigrationService) {
        this.feishuBotService = feishuBotService;
        this.profileService = profileService;
        this.recommendationService = recommendationService;
        this.activityService = activityService;
        this.visitRecordService = visitRecordService;
        this.dataMigrationService = dataMigrationService;
    }

    @PostMapping("/integrations/feishu/push-recommendations")
    public FeishuPushResponse pushFeishuRecommendations(@RequestBody FeishuPushRequest request) {
        return feishuBotService.pushRecommendations(request.userId(), request.webhookUrl());
    }

    @GetMapping("/mini/users/{userId}/dashboard")
    public MiniDashboardResponse miniDashboard(@PathVariable String userId) {
        var recommendations = recommendationService.autoRefreshToday(userId);
        return new MiniDashboardResponse(profileService.currentProfile(userId), recommendations);
    }

    @PostMapping("/mini/users/{userId}/activities")
    public ActivityResponse miniActivity(@PathVariable String userId, @Valid @RequestBody ActivityRequest request) {
        return activityService.record(new ActivityRequest(
                userId,
                request.type(),
                request.platform(),
                request.title(),
                request.url(),
                request.text(),
                request.occurredAt(),
                request.tags(),
                request.confidence(),
                request.dataLevel(),
                request.source(),
                request.detectionReason(),
                request.matchedKeyword()));
    }

    @PostMapping("/mini/users/{userId}/visits")
    public ActivityResponse miniVisit(@PathVariable String userId, @Valid @RequestBody VisitRecordRequest request) {
        return visitRecordService.recordVisit(new VisitRecordRequest(
                userId,
                request.title(),
                request.url(),
                request.platform(),
                request.visitedAt(),
                request.tags()));
    }

    @PostMapping("/mini/users/{userId}/search-terms")
    public ActivityResponse miniSearchTerm(@PathVariable String userId, @Valid @RequestBody SearchTermRequest request) {
        return activityService.recordSearchTerm(new SearchTermRequest(
                userId,
                request.keyword(),
                request.platform(),
                request.occurredAt()));
    }

    @PostMapping("/mini/users/{userId}/recommendations/generate")
    public List<RecommendationResponse> miniGenerateRecommendations(@PathVariable String userId) {
        return recommendationService.generateToday(userId);
    }

    @GetMapping("/admin/migration/activities")
    public ActivityBackupResponse exportActivities() {
        return dataMigrationService.exportActivities();
    }

    @GetMapping("/admin/migration/activities/export")
    public ActivityBackupResponse exportActivitiesAlias() {
        return dataMigrationService.exportActivities();
    }

    @PostMapping("/admin/migration/activities")
    public ImportBackupResponse importActivities(@RequestBody ActivityBackupResponse backup) {
        return dataMigrationService.importActivities(backup);
    }

    @PostMapping("/admin/migration/activities/import")
    public ImportBackupResponse importActivitiesAlias(@RequestBody ActivityBackupResponse backup) {
        return dataMigrationService.importActivities(backup);
    }

    @GetMapping("/admin/migration/full/export")
    public FullBackupResponse exportFull() {
        return dataMigrationService.exportFull();
    }

    @PostMapping("/admin/migration/full/import")
    public ImportFullBackupResponse importFull(@RequestBody FullBackupResponse backup) {
        return dataMigrationService.importFull(backup);
    }
}
