package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class YouTubeAdapter extends AbstractPlatformEventAdapter {

    private static final Pattern WATCH_ID = Pattern.compile("[?&]v=([A-Za-z0-9_-]{6,})");
    private static final Pattern SHORT_ID = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{6,})");
    private static final Pattern SHORTS_ID = Pattern.compile("/shorts/([A-Za-z0-9_-]{6,})");

    @Override
    protected String normalizedPlatform() {
        return "youtube";
    }

    @Override
    public UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        String inputUrl = normalizedHttpsUrl(event.url());
        String externalId = firstMatch(inputUrl, WATCH_ID, SHORT_ID, SHORTS_ID);
        if (externalId.isBlank()) {
            externalId = explicitExternalId(event);
        }
        String url = externalId.isBlank() ? inputUrl : "https://www.youtube.com/watch?v=" + externalId;
        requireUrlOrExternalId(url, externalId, "url or externalId is required for youtube events");
        Map<String, Object> raw = rawMetadata(event);
        putIfPresent(raw, "url", url);
        putIfPresent(raw, "externalId", externalId);
        return unifiedEvent(event, "youtube", eventType(event, ActivityType.WATCH), url, externalId, raw);
    }
}
