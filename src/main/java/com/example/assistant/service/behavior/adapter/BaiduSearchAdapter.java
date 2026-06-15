package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BaiduSearchAdapter extends AbstractPlatformEventAdapter {

    @Override
    protected String normalizedPlatform() {
        return "baidu";
    }

    @Override
    public UnifiedBehaviorEvent normalize(BehaviorEventRequest event) {
        Map<String, Object> raw = rawMetadata(event);
        String query = raw.get("query") == null ? "" : String.valueOf(raw.get("query")).trim();
        if (query.isBlank()) {
            query = trim(event.title());
        }
        if (query.isBlank()) {
            query = trim(event.matchedKeyword());
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException("rawMetadata.query is required for baidu search events");
        }
        String url = normalizedHttpsUrl(event.url());
        if (url.isBlank()) {
            url = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        }
        raw.put("query", query);
        putIfPresent(raw, "url", url);
        return unifiedEvent(event, "baidu", eventType(event, ActivityType.SEARCH), url, "", raw);
    }
}
