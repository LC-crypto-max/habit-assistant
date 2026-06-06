package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final AssistantProperties properties;
    private final UserActivityRepository activityRepository;
    private final ProfileService profileService;
    private final UserContext userContext;

    public ActivityService(AssistantProperties properties, UserActivityRepository activityRepository,
            ProfileService profileService, UserContext userContext) {
        this.properties = properties;
        this.activityRepository = activityRepository;
        this.profileService = profileService;
        this.userContext = userContext;
    }

    @Transactional
    public ActivityResponse record(ActivityRequest request) {
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        String userId = userContext.resolve(request.userId());
        UserActivity activity = new UserActivity(
                userId,
                request.type(),
                request.platform(),
                request.title(),
                request.url(),
                request.text(),
                occurredAt,
                request.tags(),
                confidence(request),
                dataLevel(request),
                source(request),
                detectionReason(request),
                matchedKeyword(request));
        UserActivity saved = activityRepository.save(activity);
        profileService.learnFrom(saved);
        return toResponse(saved);
    }

    @Transactional
    public ActivityResponse recordSearchTerm(SearchTermRequest request) {
        return record(new ActivityRequest(
                request.userId(),
                ActivityType.SEARCH,
                request.platform(),
                request.keyword(),
                null,
                request.keyword(),
                request.occurredAt(),
                List.of(request.keyword()),
                "HIGH",
                "PUBLIC_URL",
                "manual-input",
                "user_input",
                request.keyword()));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent() {
        return recent(properties.getUserId());
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(String userId) {
        return activityRepository.findTop30ByUserIdOrderByOccurredAtDesc(userContext.resolve(userId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ActivityResponse toResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getPlatform(),
                activity.getTitle(),
                activity.getUrl(),
                activity.getText(),
                activity.getOccurredAt(),
                activity.getTags(),
                activity.getConfidence(),
                activity.getDataLevel(),
                activity.getSource(),
                activity.getDetectionReason(),
                activity.getMatchedKeyword());
    }

    private String confidence(ActivityRequest request) {
        String explicit = normalize(request.confidence());
        if (isAllowed(explicit, "LOW", "MEDIUM", "HIGH")) {
            return explicit;
        }
        if (hasTag(request, "browser-extension") || hasTag(request, "page-visit")) {
            return "HIGH";
        }
        if (hasTag(request, "browser-history") || hasUrl(request)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String dataLevel(ActivityRequest request) {
        String explicit = normalize(request.dataLevel());
        if (isAllowed(explicit, "APP_USAGE_SNAPSHOT", "BROWSER_HISTORY", "PAGE_VISIBLE_CONTENT", "PUBLIC_URL", "OFFICIAL_API")) {
            return explicit;
        }
        if (hasTag(request, "browser-extension") || hasTag(request, "page-visit")) {
            return "PAGE_VISIBLE_CONTENT";
        }
        if (hasTag(request, "browser-history")) {
            return "BROWSER_HISTORY";
        }
        if (hasUrl(request)) {
            return "PUBLIC_URL";
        }
        return "APP_USAGE_SNAPSHOT";
    }

    private String source(ActivityRequest request) {
        if (request.source() != null && !request.source().isBlank()) {
            return request.source().trim();
        }
        if (hasTag(request, "visible-window") || "APP_USAGE_SNAPSHOT".equals(dataLevel(request))) {
            return "visible-window";
        }
        if (hasTag(request, "browser-history")) {
            return "browser-history";
        }
        if (hasTag(request, "browser-extension") || hasTag(request, "page-visit")) {
            return "browser-extension";
        }
        if (hasUrl(request)) {
            return "public-url";
        }
        return "manual-input";
    }

    private String detectionReason(ActivityRequest request) {
        String explicit = normalize(request.detectionReason());
        if (isAllowed(explicit, "process_name", "window_title", "url_domain", "browser_history", "page_visible_content", "user_input")) {
            return explicit;
        }
        if (hasTag(request, "browser-history")) {
            return "browser_history";
        }
        if (hasTag(request, "browser-extension") || hasTag(request, "page-visit")) {
            return "page_visible_content";
        }
        if (hasUrl(request)) {
            return "url_domain";
        }
        return request.title() == null || request.title().isBlank() ? "process_name" : "window_title";
    }

    private String matchedKeyword(ActivityRequest request) {
        if (request.matchedKeyword() != null && !request.matchedKeyword().isBlank()) {
            return request.matchedKeyword().trim();
        }
        if (request.url() != null && request.url().contains("xiaohongshu.com")) {
            return "xiaohongshu.com";
        }
        if (request.title() != null && request.title().contains("小红书")) {
            return "小红书";
        }
        return "";
    }

    private boolean hasUrl(ActivityRequest request) {
        return request.url() != null && request.url().startsWith("http");
    }

    private boolean hasTag(ActivityRequest request, String tag) {
        if (request.tags() == null) {
            return false;
        }
        String normalized = tag.toLowerCase(Locale.ROOT);
        return request.tags().stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private boolean isAllowed(String value, String... allowed) {
        for (String item : allowed) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
