package com.example.assistant.service.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PublicLinkNormalizer {

    private static final Pattern HTTP_URL = Pattern.compile(
            "https?://[^\\s<>\\\"',，。；！）》】]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}，。；：！？）】》」』’\"";
    private static final Set<String> SENSITIVE_OR_TRACKING_KEYS = Set.of(
            "access_token", "auth", "authorization", "code", "cookie", "key", "password",
            "session", "signature", "token", "xsec_token", "xsec_source", "share_source",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "source", "from", "feature", "si", "fbclid", "gclid");

    public String extractAndSanitize(String input, String platform) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher matcher = HTTP_URL.matcher(input.trim());
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            String sanitized = sanitize(candidate, platform);
            if (sanitized != null) {
                return sanitized;
            }
        }
        return null;
    }

    private String sanitize(String candidate, String platform) {
        try {
            URI uri = new URI(candidate);
            String scheme = lower(uri.getScheme());
            String host = lower(uri.getHost());
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isBlank() || uri.getUserInfo() != null) {
                return null;
            }
            if (isXiaohongshu(platform) && !isXiaohongshuHost(host)) {
                return null;
            }
            String query = safeQuery(uri.getRawQuery());
            return new URI(
                    "https",
                    null,
                    host,
                    uri.getPort(),
                    uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath(),
                    query,
                    null).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String safeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        List<String> safe = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (!SENSITIVE_OR_TRACKING_KEYS.contains(lower(key))) {
                safe.add(pair);
            }
        }
        return safe.isEmpty() ? null : String.join("&", safe);
    }

    private boolean isXiaohongshu(String platform) {
        String value = lower(platform).replace('-', '_');
        return "xiaohongshu".equals(value) || "xhs".equals(value);
    }

    private boolean isXiaohongshuHost(String host) {
        return host.equals("xiaohongshu.com") || host.endsWith(".xiaohongshu.com")
                || host.equals("xhslink.com") || host.endsWith(".xhslink.com");
    }

    private String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
