package com.example.assistant.service.agent;

import com.example.assistant.dto.AgentInterestItemRequest;
import com.example.assistant.dto.AgentTaskRequest;
import com.example.assistant.dto.AgentTaskResponse;
import com.example.assistant.dto.BehaviorEventBatchRequest;
import com.example.assistant.dto.BehaviorEventBatchResponse;
import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.service.UserContext;
import com.example.assistant.service.behavior.BehaviorEventService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTaskService {

    private final BehaviorEventService behaviorEventService;
    private final UserContext userContext;

    public AgentTaskService(BehaviorEventService behaviorEventService, UserContext userContext) {
        this.behaviorEventService = behaviorEventService;
        this.userContext = userContext;
    }

    @Transactional
    public AgentTaskResponse process(AgentTaskRequest request) {
        List<BehaviorEventRequest> events = toEvents(request);
        if (events.isEmpty()) {
            return new AgentTaskResponse(
                    request.taskId(),
                    adapter(request),
                    "EMPTY",
                    0,
                    0,
                    0,
                    events,
                    List.of(),
                    metadata(request));
        }

        boolean ingest = request.ingest() == null || request.ingest();
        if (!ingest) {
            return new AgentTaskResponse(
                    request.taskId(),
                    adapter(request),
                    "NORMALIZED",
                    events.size(),
                    0,
                    0,
                    events,
                    List.of(),
                    metadata(request));
        }

        BehaviorEventBatchResponse response = behaviorEventService.recordBatch(new BehaviorEventBatchRequest(events));
        return new AgentTaskResponse(
                request.taskId(),
                adapter(request),
                "INGESTED",
                events.size(),
                response.imported(),
                response.skipped(),
                events,
                response.activities(),
                metadata(request));
    }

    private List<BehaviorEventRequest> toEvents(AgentTaskRequest request) {
        String userId = userContext.resolve(request.userId());
        List<BehaviorEventRequest> events = new ArrayList<>();

        if (request.items() != null) {
            for (AgentInterestItemRequest item : request.items()) {
                BehaviorEventRequest event = fromItem(userId, request, item);
                if (hasInterestData(event)) {
                    events.add(event);
                }
            }
        }

        BehaviorEventRequest taskEvent = fromTask(userId, request);
        if (events.isEmpty() && hasInterestData(taskEvent)) {
            events.add(taskEvent);
        }

        return events;
    }

    private BehaviorEventRequest fromItem(String userId, AgentTaskRequest request, AgentInterestItemRequest item) {
        String platform = normalize(firstNonBlank(item.platform(), request.platform(), "agent"));
        return new BehaviorEventRequest(
                userId,
                platform,
                item.type() == null ? inferType(item.url(), item.title(), item.text()) : item.type(),
                adapter(request),
                item.externalId(),
                firstNonBlank(item.title(), item.summary(), item.url(), request.title()),
                item.url(),
                item.author(),
                item.summary(),
                compactText(item.title(), item.summary(), item.text()),
                item.occurredAt(),
                tags(platform, item.tags(), request.intent(), adapter(request)),
                item.confidence(),
                item.dataLevel(),
                item.detectionReason(),
                item.matchedKeyword());
    }

    private BehaviorEventRequest fromTask(String userId, AgentTaskRequest request) {
        String platform = normalize(firstNonBlank(request.platform(), adapter(request), "agent"));
        return new BehaviorEventRequest(
                userId,
                platform,
                inferType(request.url(), request.title(), request.text()),
                adapter(request),
                request.taskId(),
                firstNonBlank(request.title(), request.query(), request.summary(), request.url()),
                request.url(),
                null,
                request.summary(),
                compactText(request.query(), request.summary(), request.text()),
                null,
                tags(platform, List.of(), request.intent(), adapter(request)),
                null,
                null,
                null,
                firstNonBlank(request.query(), request.url(), platform));
    }

    private ActivityType inferType(String url, String title, String text) {
        if (!isBlank(url)) {
            return ActivityType.VISIT;
        }
        if (!isBlank(title) || !isBlank(text)) {
            return ActivityType.SEARCH;
        }
        return ActivityType.SEARCH;
    }

    private boolean hasInterestData(BehaviorEventRequest event) {
        return !isBlank(event.title()) || !isBlank(event.summary()) || !isBlank(event.text()) || !isBlank(event.url());
    }

    private List<String> tags(String platform, List<String> itemTags, String intent, String adapter) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(platform);
        tags.add("agent-task");
        if (!isBlank(adapter)) {
            tags.add(adapter);
        }
        if (!isBlank(intent)) {
            tags.add(normalize(intent));
        }
        if (itemTags != null) {
            itemTags.stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .forEach(tags::add);
        }
        return tags.stream().toList();
    }

    private String adapter(AgentTaskRequest request) {
        return normalize(firstNonBlank(request.adapter(), "agent-reach"));
    }

    private Map<String, Object> metadata(AgentTaskRequest request) {
        return request.metadata() == null ? Map.of() : request.metadata();
    }

    private String compactText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String normalize(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
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
