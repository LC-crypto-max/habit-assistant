package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class XiaohongshuAdapter extends AbstractPlatformEventAdapter {

    private static final Pattern NOTE_ID = Pattern.compile("/(?:explore|discovery/item)/([A-Za-z0-9_-]+)");

    @Override
    protected String normalizedPlatform() {
        return "xiaohongshu";
    }

    @Override
    public UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        String inputUrl = normalizedHttpsUrl(event.url());
        String externalId = firstMatch(inputUrl, NOTE_ID);
        if (externalId.isBlank()) {
            externalId = explicitExternalId(event);
        }
        String url = externalId.isBlank() ? inputUrl : "https://www.xiaohongshu.com/explore/" + externalId;
        requireUrlOrExternalId(url, externalId, "url or externalId is required for xiaohongshu events");
        Map<String, Object> raw = rawMetadata(event);
        putIfPresent(raw, "url", url);
        putIfPresent(raw, "externalId", externalId);
        return unifiedEvent(event, "xiaohongshu", eventType(event, ActivityType.VISIT), url, externalId, raw);
    }
}
