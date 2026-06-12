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

    public BehaviorEventService(ActivityService activityService, UserContext userContext,
            BehaviorEventPublisher publisher) {
        this.activityService = activityService;
        this.userContext = userContext;
        this.publisher = publisher;
    }

    @Transactional
    public BehaviorEventBatchResponse recordBatch(BehaviorEventBatchRequest request) {
        List<ActivityResponse> activities = new ArrayList<>();
        int skipped = 0;
        int messagesPublished = 0;

        for (BehaviorEventRequest event : request.events()) {
            if (isBlank(event.title()) && isBlank(event.text()) && isBlank(event.summary()) && isBlank(event.url())) {
                skipped++;
                continue;
            }
            String userId = userContext.resolve(event.userId());
            ActivityResponse saved = activityService.record(toActivityRequest(userId, event));
            activities.add(saved);
            if (publisher.publish(toMessage(userId, event, saved))) {
                messagesPublished++;
            }
        }

        return new BehaviorEventBatchResponse(activities.size(), skipped, messagesPublished, activities);
    }

    private ActivityRequest toActivityRequest(String userId, BehaviorEventRequest event) {
        String platform = normalizePlatform(event.platform());
        ActivityType type = event.type() == null ? inferType(event) : event.type();
        return new ActivityRequest(
                userId,
                type,
                platform,
                firstNonBlank(event.title(), event.summary(), event.url(), platform),
                event.url(),
                text(event),
                event.occurredAt(),
                tags(event, platform),
                event.confidence(),
                event.dataLevel(),
                firstNonBlank(event.source(), "client"),
                event.detectionReason(),
                event.matchedKeyword(),
                rawEvidence(event));
    }

    private BehaviorEventMessage toMessage(String userId, BehaviorEventRequest event, ActivityResponse saved) {
        return new BehaviorEventMessage(
                saved.id(),
                userId,
                saved.type(),
                saved.platform(),
                firstNonBlank(event.source(), "client"),
                event.externalId(),
                saved.title(),
                saved.url(),
                saved.text(),
                saved.occurredAt(),
                saved.tags(),
                LocalDateTime.now());
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

    private String text(BehaviorEventRequest event) {
        return String.join(" ",
                firstNonBlank(event.title(), ""),
                firstNonBlank(event.summaryForProfile(), ""),
                firstNonBlank(event.summary(), ""),
                firstNonBlank(event.text(), ""),
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

    private Map<String, Object> rawEvidence(BehaviorEventRequest event) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (event.rawEvidence() != null) {
            raw.putAll(event.rawEvidence());
        }
        putIfPresent(raw, "contentType", event.contentType());
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
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
