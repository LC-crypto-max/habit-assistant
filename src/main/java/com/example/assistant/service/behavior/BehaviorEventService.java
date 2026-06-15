package com.example.assistant.service.behavior;

import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.BehaviorEventBatchRequest;
import com.example.assistant.dto.BehaviorEventBatchResponse;
import com.example.assistant.dto.BehaviorEventMessage;
import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.service.ActivityService;
import com.example.assistant.service.UserContext;
import com.example.assistant.service.behavior.adapter.PlatformEventAdapter;
import com.example.assistant.service.behavior.adapter.UnifiedBehaviorEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BehaviorEventService {

    private final ActivityService activityService;
    private final UserContext userContext;
    private final BehaviorEventPublisher publisher;
    private final List<PlatformEventAdapter> adapters;
    private final BehaviorEventValidator validator;

    public BehaviorEventService(ActivityService activityService, UserContext userContext,
            BehaviorEventPublisher publisher, List<PlatformEventAdapter> adapters, BehaviorEventValidator validator) {
        this.activityService = activityService;
        this.userContext = userContext;
        this.publisher = publisher;
        this.adapters = adapters;
        this.validator = validator;
    }

    @Transactional
    public BehaviorEventBatchResponse recordBatch(BehaviorEventBatchRequest request) {
        List<ActivityResponse> activities = new ArrayList<>();
        int skipped = 0;
        int messagesPublished = 0;

        for (BehaviorEventRequest event : request.events()) {
            if (isBlank(event.title()) && isBlank(event.text()) && isBlank(event.summary()) && isBlank(event.url())
                    && isBlank(event.externalId()) && isBlank(rawValue(event, "query"))) {
                skipped++;
                continue;
            }
            String userId = userContext.resolve(event.userId());
            UnifiedBehaviorEvent unified = normalizeForIngestion(event);
            ActivityResponse saved = activityService.record(toActivityRequest(userId, event, unified));
            activities.add(saved);
            if (publisher.publish(toMessage(userId, event, saved, unified))) {
                messagesPublished++;
            }
        }

        return new BehaviorEventBatchResponse(activities.size(), skipped, messagesPublished, activities);
    }

    private ActivityRequest toActivityRequest(String userId, BehaviorEventRequest event, UnifiedBehaviorEvent unified) {
        String platform = unified.platform();
        ActivityType type = unified.eventType();
        String url = unified.url();
        String externalId = unified.externalId();
        return new ActivityRequest(
                userId,
                type,
                platform,
                firstNonBlank(unified.title(), unified.contentSnippet(), url, platform),
                url,
                text(unified, event.text()),
                unified.occurredAt(),
                tags(event, platform),
                event.confidence(),
                event.dataLevel(),
                firstNonBlank(event.source(), "client"),
                event.detectionReason(),
                event.matchedKeyword(),
                rawEvidence(event, unified));
    }

    private BehaviorEventMessage toMessage(String userId, BehaviorEventRequest event, ActivityResponse saved,
            UnifiedBehaviorEvent unified) {
        return new BehaviorEventMessage(
                saved.id(),
                userId,
                saved.type(),
                saved.platform(),
                firstNonBlank(event.source(), "client"),
                unified.externalId(),
                saved.title(),
                saved.url(),
                saved.text(),
                saved.occurredAt(),
                saved.tags(),
                LocalDateTime.now());
    }

    private UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        if (event.type() == ActivityType.APP_USAGE
                || "visible-window".equalsIgnoreCase(firstNonBlank(event.source(), ""))
                || "APP_USAGE_SNAPSHOT".equalsIgnoreCase(firstNonBlank(event.dataLevel(), ""))) {
            return legacyNormalize(event);
        }
        return adapters.stream()
                .filter(adapter -> adapter.supports(event.platform()))
                .findFirst()
                .map(adapter -> adapter.normalize(event))
                .orElseGet(() -> legacyNormalize(event));
    }

    private UnifiedBehaviorEvent normalizeForIngestion(BehaviorEventRequest event) {
        try {
            UnifiedBehaviorEvent unified = normalize(event);
            validator.validate(event, unified);
            return unified;
        } catch (IllegalArgumentException exception) {
            validator.logRejected(event, exception.getMessage());
            throw exception;
        }
    }

    private UnifiedBehaviorEvent legacyNormalize(BehaviorEventRequest event) {
        String platform = normalizePlatform(event.platform());
        String url = normalizeLooseUrl(event.url());
        String externalId = firstNonBlank(event.externalId(), legacyExternalId(event));
        Map<String, Object> raw = new LinkedHashMap<>();
        if (event.rawEvidence() != null) {
            raw.putAll(event.rawEvidence());
        }
        putIfPresent(raw, "url", url);
        putIfPresent(raw, "externalId", externalId);
        putIfPresent(raw, "title", event.title());
        putIfPresent(raw, "author", event.author());
        putIfPresent(raw, "contentSnippet", event.summary());
        putIfPresent(raw, "contentType", event.contentType());
        putIfPresent(raw, "interestCategory", event.contentCategory());
        putIfPresent(raw, "contentCategory", event.contentCategory());
        putIfPresent(raw, "intent", event.intent());
        putIfPresent(raw, "summaryForProfile", event.summaryForProfile());
        if (event.recommendationHints() != null && !event.recommendationHints().isEmpty()) {
            raw.put("recommendationHints", event.recommendationHints());
        }
        return new UnifiedBehaviorEvent(
                event.userId(),
                platform,
                event.type() == null ? inferType(event) : event.type(),
                url,
                externalId,
                firstNonBlank(event.title(), ""),
                firstNonBlank(event.author(), ""),
                firstNonBlank(event.summary(), ""),
                event.occurredAt(),
                raw);
    }

    private String legacyExternalId(BehaviorEventRequest event) {
        if (event.type() != ActivityType.APP_USAGE
                && !"visible-window".equalsIgnoreCase(firstNonBlank(event.source(), ""))
                && !"APP_USAGE_SNAPSHOT".equalsIgnoreCase(firstNonBlank(event.dataLevel(), ""))) {
            return "";
        }
        String basis = firstNonBlank(
                rawValue(event, "processName"),
                rawValue(event, "windowTitle"),
                event.title(),
                event.platform());
        if (isBlank(basis)) {
            return "";
        }
        String slug = basis.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60);
        }
        return "app-usage-" + slug;
    }

    private ActivityType inferType(BehaviorEventRequest event) {
        if ("visible-window".equalsIgnoreCase(firstNonBlank(event.source(), ""))
                || "APP_USAGE_SNAPSHOT".equalsIgnoreCase(firstNonBlank(event.dataLevel(), ""))) {
            return ActivityType.APP_USAGE;
        }
        if (!isBlank(event.url())) {
            return ActivityType.VISIT;
        }
        return ActivityType.SEARCH;
    }

    private String text(UnifiedBehaviorEvent event, String text) {
        return String.join(" ",
                firstNonBlank(event.title(), ""),
                firstNonBlank(String.valueOf(event.rawMetadata().getOrDefault("summaryForProfile", "")), ""),
                firstNonBlank(event.contentSnippet(), ""),
                firstNonBlank(text, ""),
                firstNonBlank(event.author(), ""),
                firstNonBlank(event.url(), ""),
                firstNonBlank(event.externalId(), ""));
    }

    private List<String> tags(BehaviorEventRequest event, String platform) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(platform);
        if (event.tags() != null) {
            event.tags().stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .forEach(tags::add);
        }
        if (event.interestTags() != null) {
            event.interestTags().stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .forEach(tags::add);
        }
        addIfPresent(tags, event.contentType());
        addIfPresent(tags, event.contentCategory());
        addIfPresent(tags, event.intent());
        return tags.stream().toList();
    }

    private Map<String, Object> rawEvidence(BehaviorEventRequest event, UnifiedBehaviorEvent unified) {
        Map<String, Object> raw = new LinkedHashMap<>(unified.rawMetadata());
        putIfPresent(raw, "platform", unified.platform());
        putIfPresent(raw, "eventType", unified.eventType().name());
        putIfPresent(raw, "url", unified.url());
        putIfPresent(raw, "externalId", unified.externalId());
        putIfPresent(raw, "title", unified.title());
        putIfPresent(raw, "author", unified.author());
        putIfPresent(raw, "contentSnippet", unified.contentSnippet());
        putIfPresent(raw, "contentType", event.contentType());
        putIfPresent(raw, "interestCategory", event.contentCategory());
        putIfPresent(raw, "contentCategory", event.contentCategory());
        putIfPresent(raw, "intent", event.intent());
        putIfPresent(raw, "summaryForProfile", event.summaryForProfile());
        if (event.recommendationHints() != null && !event.recommendationHints().isEmpty()) {
            raw.put("recommendationHints", event.recommendationHints());
        }
        return raw;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (!isBlank(value)) {
            values.add(value.trim());
        }
    }

    private void putIfPresent(Map<String, Object> values, String key, String value) {
        if (!isBlank(value)) {
            values.put(key, value.trim());
        }
    }

    private String normalizePlatform(String platform) {
        if (isBlank(platform)) {
            return "unknown";
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "baidu_search", "baidu" -> "baidu";
            case "generic_web", "browser", "generic-web" -> "web";
            case "youtube", "bilibili", "xiaohongshu" -> normalized;
            default -> normalized;
        };
    }

    private String normalizeLooseUrl(String url) {
        if (isBlank(url)) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("www.")) {
            return "https://" + normalized;
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String rawValue(BehaviorEventRequest event, String key) {
        Object value = event.rawEvidence() == null ? null : event.rawEvidence().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
