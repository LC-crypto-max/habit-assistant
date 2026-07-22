package com.example.assistant.service;

import com.example.assistant.dto.PlatformUsageSummaryResponse;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUsageSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUsageSummaryService.class);

    private final UserActivityRepository userActivityRepository;
    private final UserContext userContext;

    public PlatformUsageSummaryService(UserActivityRepository userActivityRepository, UserContext userContext) {
        this.userActivityRepository = userActivityRepository;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public PlatformUsageSummaryResponse xiaohongshu(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        List<UserActivity> activities = userActivityRepository
                .findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(resolvedUserId, LocalDateTime.now().minusHours(24));
        List<PlatformUsageSummaryResponse.Signal> windows = activities.stream()
                .filter(this::isXiaohongshuVisibleWindowSignal)
                .map(activity -> toSignal(activity, "visible-window", "LOW", "APP_USAGE_SNAPSHOT",
                        detectionReason(activity), matchedKeyword(activity)))
                .limit(20)
                .toList();
        List<PlatformUsageSummaryResponse.Signal> history = activities.stream()
                .filter(this::isXiaohongshuBrowserHistorySignal)
                .map(activity -> toSignal(activity, "browser-history", "MEDIUM", "BROWSER_HISTORY",
                        "url_domain", matchedDomain(activity.getUrl())))
                .limit(20)
                .toList();
        List<PlatformUsageSummaryResponse.Signal> pages = activities.stream()
                .filter(this::isXiaohongshuPageContentSignal)
                .map(activity -> toSignal(
                        activity,
                        firstNonBlank(activity.getSource(), "page-visit"),
                        firstNonBlank(activity.getConfidence(), "HIGH"),
                        firstNonBlank(activity.getDataLevel(), "PAGE_VISIBLE_CONTENT"),
                        firstNonBlank(activity.getDetectionReason(), "page_visible_content"),
                        firstNonBlank(activity.getMatchedKeyword(), "xiaohongshu")))
                .limit(20)
                .toList();
        String confidence = !pages.isEmpty() ? "HIGH" : !history.isEmpty() ? "MEDIUM" : !windows.isEmpty() ? "LOW" : "NONE";
        String summary = switch (confidence) {
            case "HIGH" -> "检测到你今天访问过小红书公开页面内容。";
            case "MEDIUM" -> "检测到你今天通过浏览器访问过小红书相关页面。";
            case "LOW" -> "检测到你今天可能使用过小红书相关窗口。";
            default -> "暂未检测到小红书相关使用信号。";
        };
        log.info("xiaohongshuUsageSummary userId={} windowsAppSignalCount={} browserHistorySignalCount={} pageContentSignalCount={} overallConfidence={}",
                resolvedUserId, windows.size(), history.size(), pages.size(), confidence);
        return new PlatformUsageSummaryResponse(
                "xiaohongshu",
                resolvedUserId,
                LocalDate.now(),
                summary,
                confidence,
                windows,
                history,
                pages,
                "演示真实公开笔记时，可提交小红书链接并显式授权本地 Worker 复用当前浏览器会话；系统不会保存 Cookie、Token 或 Session。");
    }

    private PlatformUsageSummaryResponse.Signal toSignal(UserActivity activity, String source, String confidence,
            String dataLevel, String detectionReason, String matchedKeyword) {
        return new PlatformUsageSummaryResponse.Signal(
                activity.getTitle(),
                activity.getUrl(),
                "",
                activity.getText(),
                source,
                confidence,
                dataLevel,
                detectionReason,
                matchedKeyword,
                activity.getOccurredAt());
    }

    private boolean isXiaohongshuVisibleWindowSignal(UserActivity activity) {
        String text = haystack(activity);
        boolean looksLikeXhs = text.contains("小红书") || text.contains("xiaohongshu") || text.contains("xhs");
        boolean visibleWindow = hasTag(activity, "visible-window") || hasTag(activity, "app-usage")
                || normalize(activity.getPlatform()).contains("local-terminal");
        return visibleWindow && looksLikeXhs && !isXiaohongshuBrowserHistorySignal(activity);
    }

    private boolean isXiaohongshuBrowserHistorySignal(UserActivity activity) {
        String url = normalize(activity.getUrl());
        return (url.contains("xiaohongshu.com") || url.contains("xhslink.com")) && hasTag(activity, "browser-history");
    }

    private boolean isXiaohongshuPageContentSignal(UserActivity activity) {
        String url = normalize(activity.getUrl());
        return (url.contains("xiaohongshu.com") || url.contains("xhslink.com"))
                && (hasTag(activity, "browser-extension")
                        || hasTag(activity, "page-visit")
                        || "PAGE_VISIBLE_CONTENT".equalsIgnoreCase(safe(activity.getDataLevel())));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String detectionReason(UserActivity activity) {
        if (matchedDomain(activity.getUrl()) != null) {
            return "url_domain";
        }
        if (normalize(activity.getTitle()).contains("小红书") || normalize(activity.getTitle()).contains("xiaohongshu")) {
            return "window_title";
        }
        return "matched_tag";
    }

    private String matchedKeyword(UserActivity activity) {
        String text = haystack(activity);
        if (text.contains("xiaohongshu.com")) {
            return "xiaohongshu.com";
        }
        if (text.contains("xhslink.com")) {
            return "xhslink.com";
        }
        if (text.contains("小红书")) {
            return "小红书";
        }
        if (text.contains("xiaohongshu")) {
            return "xiaohongshu";
        }
        return "xhs";
    }

    private String matchedDomain(String url) {
        String value = normalize(url);
        if (value.contains("xiaohongshu.com")) {
            return "xiaohongshu.com";
        }
        if (value.contains("xhslink.com")) {
            return "xhslink.com";
        }
        return null;
    }

    private boolean hasTag(UserActivity activity, String tag) {
        String normalized = normalize(tag);
        return activity.getTags().stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private String haystack(UserActivity activity) {
        return normalize(String.join(" ",
                safe(activity.getPlatform()),
                safe(activity.getTitle()),
                safe(activity.getUrl()),
                safe(activity.getText()),
                String.join(" ", activity.getTags())));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
