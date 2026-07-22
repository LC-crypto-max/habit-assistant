package com.example.assistant.service;

import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.PlatformUsageSummaryResponse;
import com.example.assistant.dto.ProfileV2Response;
import com.example.assistant.dto.RecommendationTodaySummaryResponse;
import com.example.assistant.dto.XiaohongshuDemoSnapshotResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class XiaohongshuDemoSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(XiaohongshuDemoSnapshotService.class);

    private final ActivityService activityService;
    private final PlatformUsageSummaryService platformUsageSummaryService;
    private final UserProfileBuilder userProfileBuilder;
    private final RecommendationService recommendationService;
    private final UserContext userContext;

    public XiaohongshuDemoSnapshotService(
            ActivityService activityService,
            PlatformUsageSummaryService platformUsageSummaryService,
            UserProfileBuilder userProfileBuilder,
            RecommendationService recommendationService,
            UserContext userContext) {
        this.activityService = activityService;
        this.platformUsageSummaryService = platformUsageSummaryService;
        this.userProfileBuilder = userProfileBuilder;
        this.recommendationService = recommendationService;
        this.userContext = userContext;
    }

    public XiaohongshuDemoSnapshotResponse snapshot(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        List<XiaohongshuDemoSnapshotResponse.ComponentStatus> components = new ArrayList<>();
        PlatformUsageSummaryResponse usage = null;
        List<XiaohongshuDemoSnapshotResponse.VisitAnalysis> visits = List.of();
        ProfileV2Response profile = null;
        RecommendationTodaySummaryResponse recommendations = null;

        try {
            usage = platformUsageSummaryService.xiaohongshu(resolvedUserId);
            components.add(ok("usage"));
        } catch (RuntimeException exception) {
            components.add(failed("usage", "小红书使用概况暂不可用"));
            logFailure("usage", exception);
        }
        try {
            visits = activityService.recent(resolvedUserId).stream()
                    .filter(this::isXiaohongshu)
                    .map(this::toVisitAnalysis)
                    .limit(12)
                    .toList();
            components.add(ok("visits"));
        } catch (RuntimeException exception) {
            components.add(failed("visits", "访问记录暂不可用"));
            logFailure("visits", exception);
        }
        try {
            profile = userProfileBuilder.build(resolvedUserId);
            components.add(ok("profile"));
        } catch (RuntimeException exception) {
            components.add(failed("profile", "用户画像暂不可用"));
            logFailure("profile", exception);
        }
        try {
            recommendations = recommendationService.todaySummary(resolvedUserId);
            components.add(ok("recommendations"));
        } catch (RuntimeException exception) {
            components.add(failed("recommendations", "今日推荐暂不可用"));
            logFailure("recommendations", exception);
        }

        boolean visitCaptured = !visits.isEmpty();
        boolean agentReachLive = visits.stream().anyMatch(visit ->
                "live".equalsIgnoreCase(visit.adapterMode())
                        && "SUCCESS".equalsIgnoreCase(visit.agentReachStatus()));
        boolean aiAnalyzed = visits.stream().anyMatch(visit ->
                "SUCCESS".equalsIgnoreCase(visit.llmStatus())
                        || "codex-cli-analysis".equalsIgnoreCase(visit.source()));
        boolean profileReady = profile != null && profile.topInterests() != null && !profile.topInterests().isEmpty();
        boolean recommendationsReady = recommendations != null && recommendations.recommendations() != null
                && !recommendations.recommendations().isEmpty();
        boolean degraded = components.stream().anyMatch(component -> "FAILED".equals(component.status()));
        boolean ready = !degraded && visitCaptured && agentReachLive && aiAnalyzed && profileReady && recommendationsReady;

        return new XiaohongshuDemoSnapshotResponse(
                resolvedUserId,
                LocalDateTime.now(),
                new XiaohongshuDemoSnapshotResponse.Readiness(
                        ready,
                        visitCaptured,
                        agentReachLive,
                        aiAnalyzed,
                        profileReady,
                        recommendationsReady,
                        readinessHints(visitCaptured, agentReachLive, aiAnalyzed, profileReady, recommendationsReady)),
                degraded,
                List.copyOf(components),
                usage,
                profile,
                recommendations,
                visits);
    }

    private XiaohongshuDemoSnapshotResponse.VisitAnalysis toVisitAnalysis(ActivityResponse activity) {
        Map<String, Object> raw = activity.rawEvidence() == null ? Map.of() : activity.rawEvidence();
        return new XiaohongshuDemoSnapshotResponse.VisitAnalysis(
                activity.id(),
                firstNonBlank(text(raw, "title"), activity.title()),
                activity.url(),
                text(raw, "author"),
                firstNonBlank(text(raw, "contentSnippet"), activity.text()),
                activity.tags() == null ? List.of() : activity.tags(),
                activity.occurredAt(),
                activity.confidence(),
                activity.dataLevel(),
                activity.source(),
                text(raw, "adapterMode"),
                text(raw, "agentReachStatus"),
                text(raw, "agentReachRoute"),
                text(raw, "agentReachBackend"),
                text(raw, "llm_status"),
                integer(raw.get("llm_latency_ms")),
                firstNonBlank(text(raw, "interestCategory"), text(raw, "contentCategory")),
                text(raw, "intent"));
    }

    private List<String> readinessHints(boolean visitCaptured, boolean agentReachLive, boolean aiAnalyzed,
            boolean profileReady, boolean recommendationsReady) {
        List<String> hints = new ArrayList<>();
        if (!visitCaptured) {
            hints.add("先提交一条本人授权的小红书公开笔记链接，并启动本地 Worker。");
        }
        if (visitCaptured && !agentReachLive) {
            hints.add("重新运行任务并授权复用当前浏览器登录会话，确认 OpenCLI 能读取该公开链接。");
        }
        if (visitCaptured && !aiAnalyzed) {
            hints.add("确认 Codex CLI 已登录且可执行，再重新处理该任务。");
        }
        if (!profileReady) {
            hints.add("至少保留一条中高置信度内容信号，画像才会形成稳定兴趣标签。");
        }
        if (!recommendationsReady) {
            hints.add("点击重新生成推荐，让最新画像形成今日推荐。");
        }
        if (hints.isEmpty()) {
            hints.add("演示链路已就绪：访问记录、Agent Reach、Codex、画像和推荐均有结果。");
        }
        return List.copyOf(hints);
    }

    private XiaohongshuDemoSnapshotResponse.ComponentStatus ok(String component) {
        return new XiaohongshuDemoSnapshotResponse.ComponentStatus(component, "UP", "数据读取成功");
    }

    private XiaohongshuDemoSnapshotResponse.ComponentStatus failed(String component, String message) {
        return new XiaohongshuDemoSnapshotResponse.ComponentStatus(component, "FAILED", message);
    }

    private void logFailure(String component, RuntimeException exception) {
        log.error("Xiaohongshu snapshot component failed component={} exceptionType={}",
                component, exception.getClass().getSimpleName(), exception);
    }

    private boolean isXiaohongshu(ActivityResponse activity) {
        String platform = normalize(activity.platform());
        String url = normalize(activity.url());
        return "xiaohongshu".equals(platform)
                || url.contains("xiaohongshu.com")
                || url.contains("xhslink.com");
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
