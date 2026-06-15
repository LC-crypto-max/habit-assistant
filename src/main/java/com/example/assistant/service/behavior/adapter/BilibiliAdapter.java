package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BilibiliAdapter extends AbstractPlatformEventAdapter {

    private static final Pattern VIDEO_ID = Pattern.compile("/video/((?:BV|av)[A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    @Override
    protected String normalizedPlatform() {
        return "bilibili";
    }

    @Override
    public UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        String inputUrl = normalizedHttpsUrl(event.url());
        String externalId = firstMatch(inputUrl, VIDEO_ID);
        if (externalId.isBlank()) {
            externalId = explicitExternalId(event);
        }
        String url = externalId.isBlank() ? inputUrl : "https://www.bilibili.com/video/" + externalId;
        requireUrlOrExternalId(url, externalId, "url or externalId is required for bilibili events");
        Map<String, Object> raw = rawMetadata(event);
        putIfPresent(raw, "url", url);
        putIfPresent(raw, "externalId", externalId);
        return unifiedEvent(event, "bilibili", eventType(event, ActivityType.WATCH), url, externalId, raw);
    }
}
