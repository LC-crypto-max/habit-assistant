package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.DailyProfileResponse;
import com.example.assistant.dto.InterestTermResponse;
import com.example.assistant.dto.ProfileWindowResponse;
import com.example.assistant.dto.ProfileResponse;
import com.example.assistant.dto.UserSummaryResponse;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.InterestTerm;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.InterestTermRepository;
import com.example.assistant.repo.UserActivityRepository;
import com.example.assistant.service.profile.ProfileCache;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final AssistantProperties properties;
    private final InterestTermRepository interestTermRepository;
    private final UserActivityRepository activityRepository;
    private final KeywordExtractor keywordExtractor;
    private final UserContext userContext;
    private final ProfileCache profileCache;

    public ProfileService(AssistantProperties properties, InterestTermRepository interestTermRepository,
            UserActivityRepository activityRepository, KeywordExtractor keywordExtractor, UserContext userContext,
            ProfileCache profileCache) {
        this.properties = properties;
        this.interestTermRepository = interestTermRepository;
        this.activityRepository = activityRepository;
        this.keywordExtractor = keywordExtractor;
        this.userContext = userContext;
        this.profileCache = profileCache;
    }

    @Transactional
    public void learnFrom(UserActivity activity) {
        if (!usableForProfile(activity)) {
            return;
        }
        List<String> terms = keywordExtractor.extract(joinText(activity), activity.getTags());
        double delta = weightFor(activity.getType());
        LocalDateTime seenAt = activity.getOccurredAt() == null ? LocalDateTime.now() : activity.getOccurredAt();
        for (String term : terms) {
            InterestTerm interestTerm = interestTermRepository.findByUserIdAndTerm(activity.getUserId(), term)
                    .orElseGet(() -> new InterestTerm(activity.getUserId(), term, 0, seenAt));
            interestTerm.reinforce(delta, seenAt);
            interestTermRepository.save(interestTerm);
        }
    }

    @Transactional
    public void initializeDefaultTerms() {
        initializeDefaultTerms(properties.getUserId());
    }

    @Transactional
    public void initializeDefaultTerms(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        for (String keyword : properties.getKeywords()) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String term = keyword.trim().toLowerCase();
            InterestTerm interestTerm = interestTermRepository.findByUserIdAndTerm(resolvedUserId, term)
                    .orElseGet(() -> new InterestTerm(resolvedUserId, term, 0, LocalDateTime.now()));
            interestTerm.reinforce(1.5, LocalDateTime.now());
            interestTermRepository.save(interestTerm);
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse currentProfile() {
        return currentProfile(properties.getUserId());
    }

    @Transactional(readOnly = true)
    public ProfileResponse currentProfile(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        List<InterestTermResponse> terms = topTerms(resolvedUserId).stream()
                .map(term -> new InterestTermResponse(
                        term.getTerm(),
                        round(term.getWeight()),
                        term.getHitCount(),
                        term.getLastSeenAt()))
                .toList();
        List<ActivityResponse> activities = activityRepository.findTop30ByUserIdOrderByOccurredAtDesc(resolvedUserId)
                .stream()
                .map(activity -> new ActivityResponse(
                        activity.getId(),
                        activity.getType(),
                        activity.getPlatform(),
                        activity.getTitle(),
                        activity.getUrl(),
                        activity.getText(),
                        activity.getOccurredAt(),
                        activity.getTags(),
                        activity.getConfidence(),
                        activity.getDataLevel(),
                        activity.getSource(),
                        activity.getDetectionReason(),
                        activity.getMatchedKeyword()))
                .toList();
        return new ProfileResponse(resolvedUserId, terms, activities);
    }

    @Transactional(readOnly = true)
    public DailyProfileResponse dailyProfile(String userId, boolean refresh) {
        String resolvedUserId = userContext.resolve(userId);
        LocalDate profileDate = LocalDate.now();
        if (!refresh) {
            return profileCache.get(resolvedUserId, profileDate)
                    .map(profile -> profile.withCached(true))
                    .orElseGet(() -> buildAndCacheDailyProfile(resolvedUserId, profileDate));
        }
        return buildAndCacheDailyProfile(resolvedUserId, profileDate);
    }

    @Transactional(readOnly = true)
    public List<InterestTerm> topTerms() {
        return topTerms(properties.getUserId());
    }

    @Transactional(readOnly = true)
    public List<InterestTerm> topTerms(String userId) {
        return interestTermRepository.findTop20ByUserIdOrderByWeightDescLastSeenAtDesc(userContext.resolve(userId));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> users() {
        return activityRepository.findDistinctUserIds().stream()
                .map(userId -> new UserSummaryResponse(userId, activityRepository.countByUserId(userId)))
                .toList();
    }

    private DailyProfileResponse buildAndCacheDailyProfile(String userId, LocalDate profileDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        List<UserActivity> last30Activities =
                activityRepository.findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo);
        List<UserActivity> last7Activities = last30Activities.stream()
                .filter(activity -> activity.getOccurredAt() != null && activity.getOccurredAt().isAfter(now.minusDays(7)))
                .toList();
        List<UserActivity> trustedLast30Activities = last30Activities.stream()
                .filter(this::usableForProfile)
                .toList();
        List<UserActivity> trustedLast7Activities = last7Activities.stream()
                .filter(this::usableForProfile)
                .toList();

        ProfileWindowResponse last7Days = windowProfile(7, trustedLast7Activities);
        ProfileWindowResponse last30Days = windowProfile(30, trustedLast30Activities);
        List<InterestTermResponse> topTags = mergeTopTags(last7Days.tags(), last30Days.tags());
        List<ActivityResponse> recentActivities = last30Activities.stream()
                .limit(30)
                .map(this::toActivityResponse)
                .toList();
        DailyProfileResponse profile = new DailyProfileResponse(
                userId,
                profileDate,
                now,
                summarize(userId, last7Days, last30Days, topTags),
                last7Days,
                last30Days,
                topTags,
                recentActivities,
                false);
        profileCache.put(userId, profileDate, profile);
        return profile;
    }

    private ProfileWindowResponse windowProfile(int days, List<UserActivity> activities) {
        List<InterestTermResponse> tags = aggregateTags(activities);
        return new ProfileWindowResponse(
                days,
                activities.size(),
                countTypes(activities),
                countPlatforms(activities),
                tags);
    }

    private List<InterestTermResponse> aggregateTags(List<UserActivity> activities) {
        Map<String, TermScore> scores = new LinkedHashMap<>();
        for (UserActivity activity : activities) {
            double delta = Math.max(0.5, weightFor(activity.getType()));
            LocalDateTime seenAt = activity.getOccurredAt() == null ? LocalDateTime.now() : activity.getOccurredAt();
            keywordExtractor.extract(joinText(activity), activity.getTags()).forEach(term -> {
                TermScore score = scores.computeIfAbsent(term, key -> new TermScore());
                score.weight += delta;
                score.hitCount += 1;
                if (score.lastSeenAt == null || seenAt.isAfter(score.lastSeenAt)) {
                    score.lastSeenAt = seenAt;
                }
            });
        }
        return scores.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, TermScore>>comparingDouble(entry -> entry.getValue().weight)
                        .reversed()
                        .thenComparing(entry -> entry.getKey()))
                .limit(20)
                .map(entry -> new InterestTermResponse(
                        entry.getKey(),
                        round(entry.getValue().weight),
                        entry.getValue().hitCount,
                        entry.getValue().lastSeenAt))
                .toList();
    }

    private Map<String, Long> countTypes(List<UserActivity> activities) {
        return activities.stream()
                .collect(Collectors.groupingBy(
                        activity -> activity.getType() == null ? "UNKNOWN" : activity.getType().name(),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private Map<String, Long> countPlatforms(List<UserActivity> activities) {
        return activities.stream()
                .collect(Collectors.groupingBy(
                        activity -> normalize(activity.getPlatform()),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private List<InterestTermResponse> mergeTopTags(List<InterestTermResponse> first, List<InterestTermResponse> second) {
        Map<String, TermScore> scores = new LinkedHashMap<>();
        merge(scores, first, 1.2);
        merge(scores, second, 0.8);
        return scores.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, TermScore>>comparingDouble(entry -> entry.getValue().weight)
                        .reversed()
                        .thenComparing(entry -> entry.getKey()))
                .limit(20)
                .map(entry -> new InterestTermResponse(
                        entry.getKey(),
                        round(entry.getValue().weight),
                        entry.getValue().hitCount,
                        entry.getValue().lastSeenAt))
                .toList();
    }

    private void merge(Map<String, TermScore> scores, List<InterestTermResponse> terms, double ratio) {
        for (InterestTermResponse term : terms) {
            TermScore score = scores.computeIfAbsent(term.term(), key -> new TermScore());
            score.weight += term.weight() * ratio;
            score.hitCount += term.hitCount();
            if (score.lastSeenAt == null || term.lastSeenAt() != null && term.lastSeenAt().isAfter(score.lastSeenAt)) {
                score.lastSeenAt = term.lastSeenAt();
            }
        }
    }

    private String summarize(String userId, ProfileWindowResponse last7Days, ProfileWindowResponse last30Days,
            List<InterestTermResponse> topTags) {
        String tags = topTags.stream()
                .limit(5)
                .map(InterestTermResponse::term)
                .collect(Collectors.joining("、"));
        if (tags.isBlank()) {
            return "用户 " + userId + " 最近行为较少，建议先导入浏览历史或提交搜索词，系统会逐步生成兴趣画像。";
        }
        String activePlatform = last7Days.platformCounts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知平台");
        return "用户 " + userId + " 最近 7 天产生 " + last7Days.activityCount()
                + " 条行为，30 天累计 " + last30Days.activityCount()
                + " 条。当前主要兴趣集中在 " + tags
                + "，近期活跃平台以 " + activePlatform + " 为主。";
    }

    private ActivityResponse toActivityResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getPlatform(),
                activity.getTitle(),
                activity.getUrl(),
                activity.getText(),
                activity.getOccurredAt(),
                activity.getTags(),
                activity.getConfidence(),
                activity.getDataLevel(),
                activity.getSource(),
                activity.getDetectionReason(),
                activity.getMatchedKeyword());
    }

    private String joinText(UserActivity activity) {
        return String.join(" ",
                activity.getTitle() == null ? "" : activity.getTitle(),
                activity.getText() == null ? "" : activity.getText(),
                activity.getPlatform() == null ? "" : activity.getPlatform());
    }

    private boolean usableForProfile(UserActivity activity) {
        if (activity == null) {
            return false;
        }
        String title = normalize(activity.getTitle());
        String text = normalize(activity.getText());
        if (title.isBlank() && text.isBlank()) {
            return false;
        }
        if (looksMojibake(title) || looksMojibake(text)) {
            return false;
        }
        if (isSystemWindow(title) || isSystemWindow(normalize(activity.getPlatform()))) {
            return false;
        }
        String confidence = normalize(activity.getConfidence()).toUpperCase(Locale.ROOT);
        String dataLevel = normalize(activity.getDataLevel()).toUpperCase(Locale.ROOT);
        if ("LOW".equals(confidence) && "APP_USAGE_SNAPSHOT".equals(dataLevel)) {
            return false;
        }
        return true;
    }

    private boolean looksMojibake(String value) {
        return value.contains("�") || value.contains("鏆") || value.contains("灏") || value.contains("绔");
    }

    private boolean isSystemWindow(String value) {
        return value.contains("windowsterminal")
                || value.contains("textinputhost")
                || value.contains("systemsettings")
                || value.contains("settings")
                || value.contains("powershell")
                || value.contains("cmd.exe");
    }

    private double weightFor(ActivityType type) {
        return switch (type) {
            case SEARCH -> 3.0;
            case VISIT -> 1.5;
            case WATCH -> 2.0;
            case LIKE, FAVORITE -> 5.0;
            case DISLIKE -> -4.0;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class TermScore {
        private double weight;
        private int hitCount;
        private LocalDateTime lastSeenAt;
    }
}
