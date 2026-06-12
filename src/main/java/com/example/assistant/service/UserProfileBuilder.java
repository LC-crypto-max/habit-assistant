package com.example.assistant.service;

import com.example.assistant.dto.ProfileV2Response;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileBuilder {

    private static final Set<String> SYSTEM_TERMS = Set.of(
            "windowsterminal",
            "textinputhost",
            "systemsettings",
            "powershell",
            "cmd.exe");
    private static final List<String> PLATFORM_KEYWORDS = List.of(
            "xiaohongshu", "小红书", "youtube", "bilibili", "b站", "wechat", "微信",
            "github", "zhihu", "csdn", "juejin", "java", "spring", "redis");

    private final UserActivityRepository activityRepository;
    private final UserContext userContext;

    public UserProfileBuilder(UserActivityRepository activityRepository, UserContext userContext) {
        this.activityRepository = activityRepository;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public ProfileV2Response build(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        LocalDateTime now = LocalDateTime.now();
        List<UserActivity> last7Days = activityRepository.findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
                resolvedUserId, now.minusDays(7));
        List<UserActivity> usable = deduplicate(last7Days.stream()
                .filter(this::usableEvidence)
                .toList());
        List<UserActivity> last24Hours = usable.stream()
                .filter(activity -> occurredAt(activity).isAfter(now.minusHours(24)))
                .toList();

        List<UserActivity> scoringActivities = !last24Hours.isEmpty() ? last24Hours : usable;
        List<ProfileV2Response.TopInterest> interests = topInterests(scoringActivities);
        List<ProfileV2Response.PlatformPreference> platforms = platformPreferences(scoringActivities);
        List<ProfileV2Response.Evidence> evidence = usable.stream()
                .limit(30)
                .map(this::toEvidence)
                .toList();
        return new ProfileV2Response(
                resolvedUserId,
                "v2",
                now,
                interests,
                platforms,
                summary(resolvedUserId, interests, platforms, evidence),
                evidence);
    }

    private List<ProfileV2Response.TopInterest> topInterests(List<UserActivity> activities) {
        Map<String, Score> scores = new LinkedHashMap<>();
        for (UserActivity activity : activities) {
            double weight = evidenceWeight(activity);
            for (String label : labels(activity)) {
                Score score = scores.computeIfAbsent(label, ignored -> new Score());
                score.value += weight;
                score.count += 1;
                score.confidence = stronger(score.confidence, confidence(activity));
            }
        }
        double max = scores.values().stream().mapToDouble(score -> score.value).max().orElse(1.0);
        return scores.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Score>>comparingDouble(entry -> entry.getValue().value)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(10)
                .map(entry -> new ProfileV2Response.TopInterest(
                        entry.getKey(),
                        round(entry.getValue().value / max),
                        entry.getValue().count,
                        entry.getValue().confidence))
                .toList();
    }

    private List<ProfileV2Response.PlatformPreference> platformPreferences(List<UserActivity> activities) {
        Map<String, Score> scores = new LinkedHashMap<>();
        for (UserActivity activity : activities) {
            String platform = normalize(activity.getPlatform());
            if (platform.isBlank() || "unknown".equals(platform) || "desktop-app".equals(platform)) {
                continue;
            }
            Score score = scores.computeIfAbsent(platform, ignored -> new Score());
            score.value += evidenceWeight(activity);
            score.count += 1;
            score.dataLevel = betterDataLevel(score.dataLevel, dataLevel(activity));
        }
        double max = scores.values().stream().mapToDouble(score -> score.value).max().orElse(1.0);
        return scores.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Score>>comparingDouble(entry -> entry.getValue().value)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(entry -> new ProfileV2Response.PlatformPreference(
                        entry.getKey(),
                        round(entry.getValue().value / max),
                        entry.getValue().count,
                        entry.getValue().dataLevel))
                .toList();
    }

    private List<String> labels(UserActivity activity) {
        Set<String> labels = new LinkedHashSet<>();
        if (activity.getTags() != null) {
            for (String tag : activity.getTags()) {
                String normalized = tag == null ? "" : tag.trim();
                if (usefulLabel(normalized)) {
                    labels.add(normalized);
                }
            }
        }
        String text = normalize(String.join(" ",
                activity.getTitle() == null ? "" : activity.getTitle(),
                activity.getUrl() == null ? "" : activity.getUrl(),
                activity.getText() == null ? "" : activity.getText()));
        if (text.contains("java") || text.contains("spring") || text.contains("redis")) {
            labels.add("Java后端");
        }
        if (text.contains("xiaohongshu.com") || text.contains("xhslink.com") || text.contains("小红书")) {
            labels.add("小红书");
            labels.add("生活方式");
        }
        if (text.contains("youtube.com") || text.contains("youtu.be")) {
            labels.add("视频学习");
        }
        if (text.contains("bilibili.com") || text.contains("b23.tv") || text.contains("b站")) {
            labels.add("B站视频");
        }
        return new ArrayList<>(labels);
    }

    private boolean usefulLabel(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        String normalized = normalize(label);
        return !Set.of("visible-window", "browser-history", "agent-task", "agent-reach", "app-usage",
                "public-url", "page-visit", "desktop", "browser").contains(normalized);
    }

    private List<UserActivity> deduplicate(List<UserActivity> activities) {
        Set<String> seen = new LinkedHashSet<>();
        List<UserActivity> result = new ArrayList<>();
        for (UserActivity activity : activities) {
            String key = normalize(String.join("|",
                    activity.getSource() == null ? "" : activity.getSource(),
                    activity.getPlatform() == null ? "" : activity.getPlatform(),
                    activity.getUrl() == null ? "" : activity.getUrl(),
                    activity.getTitle() == null ? "" : activity.getTitle()));
            if (seen.add(key)) {
                result.add(activity);
            }
        }
        return result;
    }

    private boolean usableEvidence(UserActivity activity) {
        if (activity == null) {
            return false;
        }
        String title = normalize(activity.getTitle());
        String text = normalize(activity.getText());
        String summary = text;
        String url = activity.getUrl() == null ? "" : activity.getUrl().trim();
        if (title.isBlank()) {
            return false;
        }
        if (summary.isBlank() && url.isBlank()) {
            return false;
        }
        if (looksMojibake(title) || looksMojibake(text)) {
            return false;
        }
        if (isSystemText(title) || isSystemText(text) || isSystemText(normalize(activity.getPlatform()))) {
            return false;
        }
        if (url.isBlank() && !hasPlatformKeyword(activity)) {
            return false;
        }
        if ("LOW".equals(confidence(activity)) && !hasPlatformKeyword(activity)) {
            return false;
        }
        return true;
    }

    private boolean hasPlatformKeyword(UserActivity activity) {
        String text = normalize(String.join(" ",
                activity.getPlatform() == null ? "" : activity.getPlatform(),
                activity.getTitle() == null ? "" : activity.getTitle(),
                activity.getText() == null ? "" : activity.getText(),
                activity.getTags() == null ? "" : String.join(" ", activity.getTags())));
        return PLATFORM_KEYWORDS.stream().anyMatch(text::contains);
    }

    private ProfileV2Response.Evidence toEvidence(UserActivity activity) {
        return new ProfileV2Response.Evidence(
                activity.getId() == null ? "" : String.valueOf(activity.getId()),
                activity.getTitle(),
                activity.getUrl(),
                activity.getSource(),
                activity.getConfidence());
    }

    private String summary(String userId, List<ProfileV2Response.TopInterest> interests,
            List<ProfileV2Response.PlatformPreference> platforms, List<ProfileV2Response.Evidence> evidence) {
        if (evidence.isEmpty()) {
            return "用户 " + userId + " 暂无可用于画像的真实访问证据。";
        }
        String top = interests.stream().limit(3).map(ProfileV2Response.TopInterest::name)
                .reduce((left, right) -> left + "、" + right).orElse("未形成稳定兴趣");
        String platform = platforms.stream().findFirst()
                .map(ProfileV2Response.PlatformPreference::platform)
                .orElse("未知平台");
        return "用户 " + userId + " 当前主要兴趣集中在 " + top + "，近期偏好平台为 " + platform + "。";
    }

    private double evidenceWeight(UserActivity activity) {
        double base = switch (confidence(activity)) {
            case "HIGH" -> 3.0;
            case "MEDIUM" -> 2.0;
            default -> 0.35;
        };
        String dataLevel = dataLevel(activity);
        if ("PAGE_VISIBLE_CONTENT".equals(dataLevel)) {
            base += 1.0;
        } else if ("BROWSER_HISTORY".equals(dataLevel)) {
            base += 0.6;
        }
        return base;
    }

    private String stronger(String left, String right) {
        return confidenceRank(right) > confidenceRank(left) ? right : left;
    }

    private int confidenceRank(String value) {
        return switch (value == null ? "" : value) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String betterDataLevel(String left, String right) {
        return dataLevelRank(right) > dataLevelRank(left) ? right : left;
    }

    private int dataLevelRank(String value) {
        return switch (value == null ? "" : value) {
            case "PAGE_VISIBLE_CONTENT" -> 4;
            case "BROWSER_HISTORY" -> 3;
            case "PUBLIC_URL", "LOCAL_NOTE" -> 2;
            case "APP_USAGE_SNAPSHOT" -> 1;
            default -> 0;
        };
    }

    private LocalDateTime occurredAt(UserActivity activity) {
        return activity.getOccurredAt() == null ? LocalDateTime.MIN : activity.getOccurredAt();
    }

    private String confidence(UserActivity activity) {
        String confidence = activity.getConfidence();
        return confidence == null || confidence.isBlank() ? "LOW" : confidence.trim().toUpperCase(Locale.ROOT);
    }

    private String dataLevel(UserActivity activity) {
        String dataLevel = activity.getDataLevel();
        return dataLevel == null || dataLevel.isBlank() ? "APP_USAGE_SNAPSHOT" : dataLevel.trim().toUpperCase(Locale.ROOT);
    }

    private boolean looksMojibake(String value) {
        return value.contains("锟") || value.contains("閺") || value.contains("鐏") || value.contains("缁");
    }

    private boolean isSystemText(String value) {
        return SYSTEM_TERMS.stream().anyMatch(value::contains);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class Score {
        private double value;
        private int count;
        private String confidence = "LOW";
        private String dataLevel = "APP_USAGE_SNAPSHOT";
    }
}
