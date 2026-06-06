package com.example.assistant.codexagent.service;

import com.example.assistant.codexagent.collector.DataCollector;
import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.AgentTaskResponse;
import com.example.assistant.codexagent.dto.AppUsageEventDTO;
import com.example.assistant.codexagent.dto.ClientAppUsageRequest;
import com.example.assistant.codexagent.dto.DailyProfileDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.dto.RecommendationDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.policy.PolicyCheckResult;
import com.example.assistant.codexagent.profile.InterestProfileBuilder;
import com.example.assistant.codexagent.recommender.ContentRecommender;
import com.example.assistant.codexagent.sanitizer.PrivacySanitizer;
import com.example.assistant.codexagent.storage.JsonFileStore;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CodexDataAgentService {

    private final UserDataAuthorizationService authorizationService;
    private final List<DataCollector> collectors;
    private final DataPolicyChecker policyChecker;
    private final PrivacySanitizer sanitizer;
    private final InterestProfileBuilder profileBuilder;
    private final ContentRecommender recommender;
    private final JsonFileStore store;
    private final CodexAgentProperties properties;

    public CodexDataAgentService(UserDataAuthorizationService authorizationService, List<DataCollector> collectors,
            DataPolicyChecker policyChecker, PrivacySanitizer sanitizer, InterestProfileBuilder profileBuilder,
            ContentRecommender recommender, JsonFileStore store, CodexAgentProperties properties) {
        this.authorizationService = authorizationService;
        this.collectors = collectors;
        this.policyChecker = policyChecker;
        this.sanitizer = sanitizer;
        this.profileBuilder = profileBuilder;
        this.recommender = recommender;
        this.store = store;
        this.properties = properties;
    }

    public AgentTaskResponse runDaily(AgentTaskRequest request) {
        UserDataAuthorization authorization = authorizationService.currentAuthorization(request.userId());
        if (authorization == null) {
            return rejected(request.taskId(), "用户未授权。");
        }
        if (authorization.isExpired() || authorization.isRevoked()) {
            return rejected(request.taskId(), "用户授权已过期或已撤销。");
        }
        List<DataAccessScope> sources = request.sources() == null || request.sources().isEmpty()
                ? authorization.getGrantedScopes()
                : request.sources();
        List<String> warnings = new ArrayList<>();
        List<InterestEventDTO> events = new ArrayList<>();
        int maxRecords = Math.min(
                request.maxRecords() == null ? properties.getLimits().getMaxRecordsPerTask() : request.maxRecords(),
                properties.getLimits().getMaxRecordsPerTask());

        for (DataAccessScope source : sources) {
            PolicyCheckResult sourceCheck = policyChecker.checkSourceAllowed(authorization, source);
            if (!sourceCheck.allowed()) {
                return new AgentTaskResponse("REJECTED", request.taskId(), 0, "", "", List.of(),
                        sourceCheck.reason(), sourceCheck.safeAlternative());
            }
            for (DataCollector collector : collectors) {
                if (!collector.supports(source.name())) {
                    continue;
                }
                List<InterestEventDTO> collected = collector.collect(request, authorization).stream()
                        .map(this::sanitize)
                        .limit(Math.max(0, maxRecords - events.size()))
                        .toList();
                events.addAll(collected);
                if (events.size() >= maxRecords) {
                    warnings.add("采集记录达到任务上限：" + maxRecords);
                    break;
                }
            }
        }

        Path eventsPath = eventsPath(request);
        for (InterestEventDTO event : events) {
            store.appendJsonLine(eventsPath, event);
        }
        DailyProfileDTO profile = profileBuilder.build(request.userId(), request.date(), events);
        int topK = request.recommendation() == null || request.recommendation().topK() == null
                ? properties.getRecommendation().getTopK()
                : request.recommendation().topK();
        List<String> categories = request.recommendation() == null ? List.of() : request.recommendation().categories();
        RecommendationDTO recommendations = recommender.recommend(profile, topK, categories);
        Path profilePath = profilePath(request);
        Path recommendationsPath = recommendationsPath(request);
        store.write(profilePath, profile);
        store.write(recommendationsPath, recommendations);
        return new AgentTaskResponse("SUCCESS", request.taskId(), events.size(), profilePath.toString(),
                recommendationsPath.toString(), warnings, "", "");
    }

    public AgentTaskResponse uploadAppUsage(ClientAppUsageRequest request) {
        UserDataAuthorization authorization = authorizationService.currentAuthorization(request.userId());
        if (authorization == null || authorization.isExpired() || authorization.isRevoked()) {
            return rejected("client_app_usage_" + request.date(), "用户未授权或授权无效。");
        }
        PolicyCheckResult sourceCheck = policyChecker.checkSourceAllowed(authorization, DataAccessScope.APP_USAGE_SUMMARY);
        if (!sourceCheck.allowed()) {
            return new AgentTaskResponse("REJECTED", "client_app_usage_" + request.date(), 0, "", "", List.of(),
                    sourceCheck.reason(), sourceCheck.safeAlternative());
        }
        List<AppUsageEventDTO> allowed = request.events() == null ? List.of() : request.events().stream()
                .filter(event -> authorization.getAllowedApps().contains(event.packageName()))
                .toList();
        Path path = Path.of(properties.getStorage().getImportsPath(), "app_usage_client_" + request.date() + ".json");
        store.write(path, allowed);
        return new AgentTaskResponse("SUCCESS", "client_app_usage_" + request.date(), allowed.size(), "", "",
                List.of("已保存 Android App Usage 概况：" + path), "", "");
    }

    private InterestEventDTO sanitize(InterestEventDTO event) {
        return new InterestEventDTO(
                event.eventId() == null ? UUID.randomUUID().toString() : event.eventId(),
                event.userId(),
                event.source(),
                sanitizer.sanitize(event.platform()),
                sanitizer.sanitize(event.eventType()),
                sanitizer.sanitize(event.title()),
                sanitizer.sanitize(event.url()),
                sanitizer.sanitize(event.author()),
                event.tags().stream().map(sanitizer::sanitize).toList(),
                sanitizer.sanitize(event.summary()),
                event.timestamp() == null ? OffsetDateTime.now() : event.timestamp(),
                event.weight(),
                event.metadata() == null ? Map.of() : event.metadata());
    }

    private AgentTaskResponse rejected(String taskId, String reason) {
        return new AgentTaskResponse("REJECTED", taskId, 0, "", "", List.of(), reason,
                "可以改为采集 App 使用概况、浏览器历史标题与 URL、公开链接元数据或本地笔记。");
    }

    private Path eventsPath(AgentTaskRequest request) {
        return Path.of(properties.getStorage().getProcessedPath(),
                "interest_events_" + request.date().toString().replace("-", "_") + ".jsonl");
    }

    private Path profilePath(AgentTaskRequest request) {
        return Path.of(properties.getStorage().getProfilesPath(),
                "daily_profile_" + request.date().toString().replace("-", "_") + ".json");
    }

    private Path recommendationsPath(AgentTaskRequest request) {
        return Path.of(properties.getStorage().getRecommendationsPath(),
                "daily_recommendations_" + request.date().toString().replace("-", "_") + ".json");
    }
}
