package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GenericWebAdapter extends AbstractPlatformEventAdapter {

    @Override
    protected String normalizedPlatform() {
        return "web";
    }

    @Override
    public UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        String url = normalizedHttpsUrl(event.url());
        String externalId = explicitExternalId(event);
        if (url.isBlank() && !externalId.isBlank()) {
            url = normalizedHttpsUrl(externalId);
        }
        requireUrlOrExternalId(url, externalId, "url or externalId is required for web events");
        Map<String, Object> raw = rawMetadata(event);
        putIfPresent(raw, "url", url);
        putIfPresent(raw, "externalId", externalId);
        return unifiedEvent(event, "web", eventType(event, ActivityType.VISIT), url, externalId, raw);
    }
}
