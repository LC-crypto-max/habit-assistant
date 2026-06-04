package com.example.assistant.service;

import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.DataSourceBatchRequest;
import com.example.assistant.dto.DataSourceBatchResponse;
import com.example.assistant.dto.DataSourceEventRequest;
import com.example.assistant.model.ActivityType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceIngestionService {

    private final ActivityService activityService;
    private final UserContext userContext;

    public DataSourceIngestionService(ActivityService activityService, UserContext userContext) {
        this.activityService = activityService;
        this.userContext = userContext;
    }

    @Transactional
    public ActivityResponse record(DataSourceEventRequest request) {
        return activityService.record(toActivityRequest(request));
    }

    @Transactional
    public DataSourceBatchResponse recordBatch(DataSourceBatchRequest request) {
        List<ActivityResponse> imported = new ArrayList<>();
        int skipped = 0;
        for (DataSourceEventRequest event : request.events()) {
            if (isBlank(event.title()) && isBlank(event.text()) && isBlank(event.url())) {
                skipped++;
                continue;
            }
            imported.add(record(event));
        }
        return new DataSourceBatchResponse(imported.size(), skipped, imported);
    }

    private ActivityRequest toActivityRequest(DataSourceEventRequest request) {
        String platform = normalizePlatform(request.platform());
        ActivityType type = request.type() == null ? inferType(request) : request.type();
        String text = text(request);
        return new ActivityRequest(
                userContext.resolve(request.userId()),
                type,
                platform,
                firstNonBlank(request.title(), request.summary(), request.url(), platform),
                request.url(),
                text,
                request.occurredAt(),
                tags(request, platform));
    }

    private ActivityType inferType(DataSourceEventRequest request) {
        if (!isBlank(request.url())) {
            return ActivityType.VISIT;
        }
        return ActivityType.SEARCH;
    }

    private String text(DataSourceEventRequest request) {
        return String.join(" ",
                firstNonBlank(request.title(), ""),
                firstNonBlank(request.summary(), ""),
                firstNonBlank(request.text(), ""),
                firstNonBlank(request.author(), ""),
                firstNonBlank(request.url(), ""),
                firstNonBlank(request.externalId(), ""));
    }

    private List<String> tags(DataSourceEventRequest request, String platform) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(platform);
        if (request.tags() != null) {
            request.tags().stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .forEach(tags::add);
        }
        return tags.stream().toList();
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
