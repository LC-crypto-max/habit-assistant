package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private static final int USER_ID_LIMIT = 120;
    private static final int PLATFORM_LIMIT = 120;
    private static final int TITLE_LIMIT = 512;
    private static final int URL_LIMIT = 1024;
    private static final int TEXT_LIMIT = 8000;
    private static final int TAG_LIMIT = 80;
    private static final int TAG_COUNT_LIMIT = 30;
    private static final int METADATA_LIMIT = 160;
    private static final int RAW_EVIDENCE_LIMIT = 12000;

    private final AssistantProperties properties;
    private final UserActivityRepository activityRepository;
    private final ProfileService profileService;
    private final UserContext userContext;
    private final ObjectMapper objectMapper;

    public ActivityService(AssistantProperties properties, UserActivityRepository activityRepository,
            ProfileService profileService, UserContext userContext, ObjectMapper objectMapper) {
        this.properties = properties;
        this.activityRepository = activityRepository;
        this.profileService = profileService;
        this.userContext = userContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ActivityResponse record(ActivityRequest request) {
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        String userId = userContext.resolve(request.userId());
        UserActivity activity = new UserActivity(
                limit(userId, USER_ID_LIMIT),
                request.type(),
                limit(request.platform(), PLATFORM_LIMIT),
                limit(request.title(), TITLE_LIMIT),
                limit(request.url(), URL_LIMIT),
                limit(request.text(), TEXT_LIMIT),
                occurredAt,
                safeTags(request.tags()),
                limit(confidence(request), METADATA_LIMIT),
                limit(dataLevel(request), METADATA_LIMIT),
                limit(source(request), METADATA_LIMIT),
                limit(detectionReason(request), METADATA_LIMIT),
                limit(matchedKeyword(request), METADATA_LIMIT),
                rawEvidenceJson(request.rawEvidence()));
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
                request.keyword(),
                Map.of("processName", "", "windowTitle", "", "domain", "", "visitCount", 0)));
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
                activity.getMatchedKeyword(),
                rawEvidence(activity));
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
        if (isAllowed(explicit, "process_name", "window_title", "url_domain", "browser_history",
                "page_visible_content", "public_url_enrichment", "user_input")) {
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

    private String rawEvidenceJson(Map<String, Object> rawEvidence) {
        if (rawEvidence == null || rawEvidence.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("browser", limit(rawEvidence.get("browser"), 80));
        safe.put("processName", limit(rawEvidence.get("processName"), 120));
        safe.put("windowTitle", limit(rawEvidence.get("windowTitle"), 240));
        safe.put("domain", limit(rawEvidence.get("domain"), 160));
        safe.put("visitCount", visitCount(rawEvidence.get("visitCount")));
        safe.put("query", limit(rawEvidence.get("query"), 240));
        safe.put("platform", limit(rawEvidence.get("platform"), 120));
        safe.put("eventType", limit(rawEvidence.get("eventType"), 80));
        safe.put("url", limit(rawEvidence.get("url"), 1024));
        safe.put("externalId", limit(rawEvidence.get("externalId"), 160));
        safe.put("title", limit(rawEvidence.get("title"), 512));
        safe.put("author", limit(rawEvidence.get("author"), 160));
        safe.put("contentSnippet", limit(rawEvidence.get("contentSnippet"), 420));
        safe.put("adapter", limit(rawEvidence.get("adapter"), 80));
        safe.put("adapterMode", limit(rawEvidence.get("adapterMode"), 80));
        safe.put("agentReachCommand", limit(rawEvidence.get("agentReachCommand"), 500));
        safe.put("originalSource", limit(rawEvidence.get("originalSource"), 120));
        safe.put("contentType", limit(rawEvidence.get("contentType"), 80));
        safe.put("interestCategory", limit(rawEvidence.get("interestCategory"), 120));
        safe.put("contentCategory", limit(rawEvidence.get("contentCategory"), 120));
        safe.put("intent", limit(rawEvidence.get("intent"), 120));
        safe.put("summaryForProfile", limit(rawEvidence.get("summaryForProfile"), 420));
        Object recommendationHints = rawEvidence.get("recommendationHints");
        if (recommendationHints instanceof List<?> hints) {
            safe.put("recommendationHints", hints.stream()
                    .map(value -> limit(value, 160))
                    .filter(value -> !value.isBlank())
                    .limit(5)
                    .toList());
        }
        try {
            return limit(objectMapper.writeValueAsString(safe), RAW_EVIDENCE_LIMIT);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> rawEvidence(UserActivity activity) {
        if (activity.getRawEvidence() == null || activity.getRawEvidence().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(activity.getRawEvidence(), new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String limit(Object value, int limit) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private int visitCount(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private List<String> safeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .map(value -> limit(value, TAG_LIMIT))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(TAG_COUNT_LIMIT)
                .toList();
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
