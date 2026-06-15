package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractPlatformEventAdapter implements PlatformEventAdapter {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "source", "from", "spm", "spm_id_from", "feature", "si", "fbclid", "gclid", "vd_source");

    @Override
    public boolean supports(String platform) {
        return normalizedPlatform().equals(normalizePlatformName(platform));
    }

    protected abstract String normalizedPlatform();

    protected ActivityType eventType(BehaviorEventRequest event, ActivityType fallback) {
        return event.type() == null ? fallback : event.type();
    }

    protected String normalizePlatformName(String platform) {
        if (isBlank(platform)) {
            return "";
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "baidu_search" -> "baidu";
            case "generic_web", "browser" -> "web";
            default -> normalized;
        };
    }

    protected String explicitExternalId(BehaviorEventRequest event) {
        return trim(event.externalId());
    }

    protected Map<String, Object> rawMetadata(BehaviorEventRequest event) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (event.rawEvidence() != null) {
            raw.putAll(event.rawEvidence());
        }
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
        return raw;
    }

    protected UnifiedBehaviorEvent unifiedEvent(BehaviorEventRequest event, String platform, ActivityType eventType,
            String url, String externalId, Map<String, Object> raw) {
        return new UnifiedBehaviorEvent(
                event.userId(),
                platform,
                eventType,
                url,
                externalId,
                trim(event.title()),
                trim(event.author()),
                trim(event.summary()),
                event.occurredAt(),
                raw);
    }

    protected String normalizedHttpsUrl(String url) {
        if (isBlank(url)) {
            return "";
        }
        String text = url.trim();
        if (text.startsWith("www.")) {
            text = "https://" + text;
        }
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            return "";
        }
        try {
            URI uri = new URI(text);
            String host = uri.getHost();
            if (isBlank(host)) {
                return "";
            }
            String query = cleanQuery(uri.getRawQuery());
            URI normalized = new URI(
                    "https",
                    null,
                    host.toLowerCase(Locale.ROOT),
                    -1,
                    blankToSlash(uri.getRawPath()),
                    query.isBlank() ? null : query,
                    null);
            return normalized.toString();
        } catch (URISyntaxException exception) {
            return "";
        }
    }

    protected String cleanQuery(String rawQuery) {
        if (isBlank(rawQuery)) {
            return "";
        }
        Map<String, String> kept = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            if (TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = parts.length > 1 ? decode(parts[1]) : "";
            kept.put(key, value);
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : kept.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encode(entry.getKey()));
            if (!entry.getValue().isBlank()) {
                query.append('=').append(encode(entry.getValue()));
            }
        }
        return query.toString();
    }

    protected String firstMatch(String value, Pattern... patterns) {
        if (isBlank(value)) {
            return "";
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    protected void putIfPresent(Map<String, Object> values, String key, String value) {
        if (!isBlank(value)) {
            values.put(key, value.trim());
        }
    }

    protected String requireUrlOrExternalId(String url, String externalId, String message) {
        if (isBlank(url) && isBlank(externalId)) {
            throw new IllegalArgumentException(message);
        }
        return url;
    }

    protected String trim(String value) {
        return value == null ? "" : value.trim();
    }

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToSlash(String path) {
        return isBlank(path) ? "/" : path;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
