package com.example.assistant.service.behavior;

import com.example.assistant.dto.BehaviorEventRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.service.behavior.adapter.UnifiedBehaviorEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BehaviorEventValidator {

    private static final Logger INVALID_EVENT_LOG = LoggerFactory.getLogger("INVALID_EVENT_LOG");

    private static final Set<String> KNOWN_PLATFORMS = Set.of(
            "youtube", "bilibili", "baidu", "xiaohongshu", "web",
            "desktop_app", "desktop-app", "wechat", "douyin", "github");

    public void validate(BehaviorEventRequest request, UnifiedBehaviorEvent event) {
        if (event.eventType() == null || request.type() == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (!KNOWN_PLATFORMS.contains(event.platform())) {
            throw new IllegalArgumentException("unknown platform: " + event.platform());
        }
        if (isBlank(event.url()) && isBlank(event.externalId())) {
            throw new IllegalArgumentException("url or externalId is required");
        }
        if (!isBlank(event.url()) && platformUrlMismatch(event.platform(), event.url())) {
            throw new IllegalArgumentException("platform/url mismatch: " + event.platform());
        }
    }

    public void logRejected(BehaviorEventRequest request, String reason) {
        INVALID_EVENT_LOG.warn("Rejected behavior event reason={} userId={} platform={} eventType={} urlHost={} externalIdPresent={}",
                safe(reason, 180),
                safe(request.userId(), 80),
                safe(request.platform(), 80),
                request.type() == null ? "" : request.type().name(),
                host(request.url()),
                !isBlank(request.externalId()));
    }

    private boolean platformUrlMismatch(String platform, String url) {
        String host = host(url);
        if (isBlank(host)) {
            return true;
        }
        return switch (platform) {
            case "youtube" -> !matches(host, "youtube.com", "youtu.be");
            case "bilibili" -> !matches(host, "bilibili.com", "b23.tv");
            case "baidu" -> !matches(host, "baidu.com");
            case "xiaohongshu" -> !matches(host, "xiaohongshu.com", "xhslink.com");
            case "github" -> !matches(host, "github.com");
            default -> false;
        };
    }

    private boolean matches(String host, String... domains) {
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private String host(String url) {
        if (isBlank(url)) {
            return "";
        }
        try {
            String host = new URI(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (URISyntaxException exception) {
            return "";
        }
    }

    private String safe(String value, int limit) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("(?i)(cookie|token|session|password|authorization)\\s*[=:]\\s*\\S+", "$1=[REDACTED]")
                .trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
