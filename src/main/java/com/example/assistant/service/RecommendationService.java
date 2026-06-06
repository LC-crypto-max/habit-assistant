package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.DailyProfileResponse;
import com.example.assistant.dto.InterestTermResponse;
import com.example.assistant.dto.RecommendationRefreshPolicyResponse;
import com.example.assistant.dto.RecommendationResponse;
import com.example.assistant.dto.RecommendationSearchResponse;
import com.example.assistant.dto.RecommendationTodaySummaryResponse;
import com.example.assistant.dto.RecommendationTodayResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.ContentItem;
import com.example.assistant.model.FeedbackType;
import com.example.assistant.model.InterestTerm;
import com.example.assistant.model.Recommendation;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.ContentItemRepository;
import com.example.assistant.repo.RecommendationRepository;
import com.example.assistant.repo.UserActivityRepository;
import com.example.assistant.service.collector.CollectedContent;
import com.example.assistant.service.collector.PlatformCollector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String EMPTY_MESSAGE = "暂无推荐数据，请先创建采集任务并启动 worker。";

    private final AssistantProperties properties;
    private final ProfileService profileService;
    private final ActivityService activityService;
    private final ContentItemRepository contentItemRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserActivityRepository userActivityRepository;
    private final List<PlatformCollector> collectors;
    private final UserContext userContext;

    public RecommendationService(AssistantProperties properties, ProfileService profileService,
            ActivityService activityService, ContentItemRepository contentItemRepository,
            RecommendationRepository recommendationRepository, UserActivityRepository userActivityRepository,
            List<PlatformCollector> collectors, UserContext userContext) {
        this.properties = properties;
        this.profileService = profileService;
        this.activityService = activityService;
        this.contentItemRepository = contentItemRepository;
        this.recommendationRepository = recommendationRepository;
        this.userActivityRepository = userActivityRepository;
        this.collectors = collectors;
        this.userContext = userContext;
    }

    @Transactional
    public List<RecommendationResponse> generateToday() {
        return generateToday(properties.getUserId());
    }

    @Transactional
    public List<RecommendationResponse> generateToday(String userId) {
        return generateToday(userId, List.of(), false);
    }

    @Transactional
    public List<RecommendationResponse> refreshToday(String userId) {
        return generateToday(userId, List.of(), true);
    }

    @Transactional
    public List<RecommendationResponse> autoRefreshToday(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        List<RecommendationResponse> existing = today(resolvedUserId);
        if (existing.isEmpty() || refreshPolicy(resolvedUserId).expired()) {
            return refreshToday(resolvedUserId);
        }
        return existing;
    }

    @Transactional
    public RecommendationTodaySummaryResponse todaySummary(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        RecommendationRefreshPolicyResponse policy = refreshPolicy(resolvedUserId);
        List<RecommendationResponse> existing = today(resolvedUserId);
        if (existing.isEmpty() && policy.recentActivityCount() <= 0) {
            return emptyTodaySummary(resolvedUserId, EMPTY_MESSAGE, policy);
        }

        List<RecommendationResponse> recommendations = existing;
        if (recommendations.isEmpty() || policy.expired()) {
            try {
                recommendations = refreshToday(resolvedUserId);
                policy = refreshPolicy(resolvedUserId);
            } catch (RuntimeException ex) {
                log.error("Failed to refresh recommendations for userId={}", resolvedUserId, ex);
                recommendations = today(resolvedUserId);
            }
        }

        if (recommendations.isEmpty()) {
            if (policy.recentActivityCount() > 0) {
                recommendations = List.of(basicRecommendation(resolvedUserId, policy.recentActivityCount()));
                log.info("todaySummary fallback generated userId={} behaviorCount24h={} recommendationCount={} status={} fallbackReason={}",
                        resolvedUserId, policy.recentActivityCount(), recommendations.size(), "SUCCESS",
                        "recent_behavior_without_recommendations");
                return new RecommendationTodaySummaryResponse(
                        resolvedUserId,
                        LocalDate.now(),
                        policy.refreshHours(),
                        policy.recentActivityCount(),
                        recommendations,
                        "未找到已有推荐，已根据近 24 小时行为生成基础推荐。",
                        "SUCCESS");
            }
            return emptyTodaySummary(resolvedUserId, EMPTY_MESSAGE, policy);
        }
        log.info("todaySummary loaded userId={} behaviorCount24h={} recommendationCount={} status={} fallbackReason={}",
                resolvedUserId, policy.recentActivityCount(), recommendations.size(), "SUCCESS", "none");
        return new RecommendationTodaySummaryResponse(
                resolvedUserId,
                LocalDate.now(),
                policy.refreshHours(),
                policy.recentActivityCount(),
                recommendations,
                "已同步最新访问记录和推荐。",
                "SUCCESS");
    }

    @Transactional(readOnly = true)
    public RecommendationTodaySummaryResponse emptyTodaySummary(String userId, String message) {
        String resolvedUserId = userContext.resolve(userId);
        return emptyTodaySummary(resolvedUserId, message, refreshPolicy(resolvedUserId));
    }

    @Transactional
    public RecommendationSearchResponse searchAndRecommend(String userId, String keyword, String platform,
            boolean refresh) {
        String resolvedUserId = userContext.resolve(userId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String sourcePlatform = platform == null || platform.isBlank() ? "web-search" : platform.trim();
        activityService.recordSearchTerm(new SearchTermRequest(resolvedUserId, normalizedKeyword, sourcePlatform, null));

        List<RecommendationResponse> recommendations = generateToday(resolvedUserId, List.of(normalizedKeyword), refresh);
        return new RecommendationSearchResponse(
                resolvedUserId,
                normalizedKeyword,
                profileService.currentProfile(resolvedUserId),
                recommendations,
                refreshPolicy(resolvedUserId));
    }

    private List<RecommendationResponse> generateToday(String userId, List<String> extraQueries, boolean replaceExisting) {
        String resolvedUserId = userContext.resolve(userId);
        LocalDate today = LocalDate.now();
        if (replaceExisting) {
            recommendationRepository.deleteByUserIdAndRecommendationDate(resolvedUserId, today);
        }

        List<InterestTerm> terms = profileService.topTerms(resolvedUserId);
        if (terms.isEmpty()) {
            profileService.initializeDefaultTerms(resolvedUserId);
            terms = profileService.topTerms(resolvedUserId);
        }
        Map<String, InterestTerm> termByName = terms.stream()
                .collect(Collectors.toMap(InterestTerm::getTerm, Function.identity(), (left, right) -> left));
        List<String> queries = searchQueries(terms, extraQueries);
        FeedbackSignals feedbackSignals = feedbackSignals(resolvedUserId);

        List<ScoredContent> scored = collectors.stream()
                .flatMap(collector -> collector.search(resolvedUserId, queries).stream())
                .map(this::saveContent)
                .filter(content -> !feedbackSignals.blockedPlatforms().contains(normalize(content.getPlatform())))
                .map(content -> new ScoredContent(content, score(content, termByName, extraQueries, feedbackSignals),
                        reason(content, termByName, extraQueries, feedbackSignals)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredContent::score).reversed())
                .limit(10)
                .toList();

        for (ScoredContent item : scored) {
            if (!recommendationRepository.existsByUserIdAndRecommendationDateAndContentItemId(
                    resolvedUserId, today, item.content().getId())) {
                recommendationRepository.save(new Recommendation(
                        resolvedUserId,
                        item.content().getId(),
                        today,
                        item.score(),
                        item.reason(),
                        LocalDateTime.now()));
            }
        }
        return today(resolvedUserId);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> today() {
        return today(properties.getUserId());
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> today(String userId) {
        LocalDate today = LocalDate.now();
        return recommendationRepository.findByUserIdAndRecommendationDateOrderByScoreDesc(userContext.resolve(userId), today)
                .stream()
                .map(this::toResponse)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public RecommendationTodayResponse todayView(String userId, boolean refresh) {
        String resolvedUserId = userContext.resolve(userId);
        List<RecommendationResponse> recommendations = refresh
                ? refreshToday(resolvedUserId)
                : autoRefreshToday(resolvedUserId);
        DailyProfileResponse profile = profileService.dailyProfile(resolvedUserId, false);
        List<String> profileTerms = profile.topTags().stream()
                .map(InterestTermResponse::term)
                .toList();
        List<RecommendationTodayResponse.Item> items = recommendations.stream()
                .map(recommendation -> toTodayItem(recommendation, profileTerms))
                .toList();
        return new RecommendationTodayResponse(
                resolvedUserId,
                LocalDate.now(),
                LocalDateTime.now(),
                summary(resolvedUserId, items, profile.summary()),
                refreshPolicy(resolvedUserId),
                profile.topTags(),
                items);
    }

    @Transactional(readOnly = true)
    public RecommendationRefreshPolicyResponse refreshPolicy(String userId) {
        String resolvedUserId = userContext.resolve(userId);
        AssistantProperties.RecommendationRefresh config = properties.getRecommendationRefresh();
        int activityWindowHours = Math.max(1, config.getActivityWindowHours());
        long recentActivityCount = userActivityRepository.countByUserIdAndOccurredAtAfter(
                resolvedUserId, LocalDateTime.now().minusHours(activityWindowHours));
        int refreshHours = refreshHours(recentActivityCount, config);
        LocalDateTime latestRecommendationAt = recommendationRepository
                .findByUserIdAndRecommendationDateOrderByCreatedAtDesc(resolvedUserId, LocalDate.now())
                .stream()
                .findFirst()
                .map(Recommendation::getCreatedAt)
                .orElse(null);
        LocalDateTime expiresAt = latestRecommendationAt == null ? null : latestRecommendationAt.plusHours(refreshHours);
        boolean expired = expiresAt == null || !expiresAt.isAfter(LocalDateTime.now());
        return new RecommendationRefreshPolicyResponse(
                resolvedUserId,
                recentActivityCount,
                activityWindowHours,
                refreshHours,
                latestRecommendationAt,
                expiresAt,
                expired);
    }

    @Transactional
    public RecommendationResponse updateFeedback(Long id, FeedbackType feedback) {
        Recommendation recommendation = recommendationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + id));
        recommendation.updateFeedback(feedback);
        Recommendation saved = recommendationRepository.save(recommendation);
        recordFeedbackActivity(saved, feedback);
        return toResponse(saved);
    }

    @Transactional
    public RecommendationResponse recordClick(Long id) {
        return updateFeedback(id, FeedbackType.READ);
    }

    @Transactional
    public RecommendationResponse markNotInterested(Long id) {
        return updateFeedback(id, FeedbackType.DISLIKE);
    }

    private ContentItem saveContent(CollectedContent content) {
        String hash = sha256(content.platform() + "|" + content.url() + "|" + content.title());
        return contentItemRepository.findByContentHash(hash)
                .orElseGet(() -> contentItemRepository.save(new ContentItem(
                        content.platform(),
                        content.externalId(),
                        content.title(),
                        content.url(),
                        content.author(),
                        content.summary(),
                        content.publishedAt(),
                        LocalDateTime.now(),
                        hash,
                        content.tags())));
    }

    private List<String> searchQueries(List<InterestTerm> terms, List<String> extraQueries) {
        Set<String> queries = new HashSet<>();
        extraQueries.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .forEach(queries::add);
        terms.stream()
                .map(InterestTerm::getTerm)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .forEach(queries::add);
        return queries.stream().toList();
    }

    private double score(ContentItem content, Map<String, InterestTerm> termByName, List<String> extraQueries,
            FeedbackSignals feedbackSignals) {
        String title = normalize(content.getTitle());
        String summary = normalize(content.getSummary());
        String tagText = normalize(String.join(" ", content.getTags()));
        String haystack = String.join(" ", title, summary, tagText);
        double score = 0;

        for (InterestTerm term : termByName.values()) {
            String normalizedTerm = normalize(term.getTerm());
            if (normalizedTerm.isBlank()) {
                continue;
            }
            double weight = Math.max(1, term.getWeight());
            if (title.contains(normalizedTerm)) {
                score += weight * 1.6;
            } else if (summary.contains(normalizedTerm)) {
                score += weight;
            } else if (tagText.contains(normalizedTerm)) {
                score += weight * 0.8;
            }
        }

        for (String query : extraQueries) {
            String normalizedQuery = normalize(query);
            if (!normalizedQuery.isBlank() && haystack.contains(normalizedQuery)) {
                score += title.contains(normalizedQuery) ? 3.0 : 1.5;
            }
        }

        score += feedbackBoost(content, feedbackSignals);
        score += recencyBoost(content.getPublishedAt());
        return Math.round(score * 100.0) / 100.0;
    }

    private double feedbackBoost(ContentItem content, FeedbackSignals feedbackSignals) {
        String platform = normalize(content.getPlatform());
        Set<String> tags = content.getTags().stream().map(this::normalize).collect(Collectors.toSet());
        double boost = 0;
        if (feedbackSignals.likedPlatforms().contains(platform)) {
            boost += 1.2;
        }
        if (feedbackSignals.dislikedPlatforms().contains(platform)) {
            boost -= 1.5;
        }
        long likedTagHits = tags.stream().filter(feedbackSignals.likedTags()::contains).count();
        long dislikedTagHits = tags.stream().filter(feedbackSignals.dislikedTags()::contains).count();
        boost += Math.min(2.0, likedTagHits * 0.6);
        boost -= Math.min(2.5, dislikedTagHits * 0.8);
        return boost;
    }

    private double recencyBoost(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0;
        }
        long hours = Duration.between(publishedAt, LocalDateTime.now()).toHours();
        if (hours <= 6) {
            return 2.0;
        }
        if (hours <= 24) {
            return 1.5;
        }
        if (hours <= 24 * 7) {
            return 0.5;
        }
        return 0;
    }

    private String reason(ContentItem content, Map<String, InterestTerm> termByName, List<String> extraQueries,
            FeedbackSignals feedbackSignals) {
        String haystack = haystack(content);
        List<String> matched = termByName.keySet().stream()
                .filter(term -> haystack.contains(normalize(term)))
                .limit(4)
                .toList();
        List<String> keywordMatched = extraQueries.stream()
                .filter(query -> !normalize(query).isBlank())
                .filter(query -> haystack.contains(normalize(query)))
                .limit(2)
                .toList();
        if (!keywordMatched.isEmpty()) {
            return "命中本次搜索关键词：" + String.join("、", keywordMatched);
        }
        if (feedbackSignals.likedPlatforms().contains(normalize(content.getPlatform()))) {
            return "与你之前喜欢的平台和标签相似。";
        }
        if (matched.isEmpty()) {
            return "根据你的默认兴趣推荐。";
        }
        return "命中你的兴趣词：" + String.join("、", matched);
    }

    private FeedbackSignals feedbackSignals(String userId) {
        Set<String> likedPlatforms = new HashSet<>();
        Set<String> dislikedPlatforms = new HashSet<>();
        Set<String> blockedPlatforms = new HashSet<>();
        Set<String> likedTags = new HashSet<>();
        Set<String> dislikedTags = new HashSet<>();

        for (Recommendation recommendation : recommendationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)) {
            FeedbackType feedback = recommendation.getFeedback();
            if (feedback == null || feedback == FeedbackType.NONE) {
                continue;
            }
            contentItemRepository.findById(recommendation.getContentItemId()).ifPresent(content -> {
                String platform = normalize(content.getPlatform());
                Set<String> tags = content.getTags().stream().map(this::normalize).collect(Collectors.toSet());
                switch (feedback) {
                    case LIKE, FAVORITE, READ -> {
                        likedPlatforms.add(platform);
                        likedTags.addAll(tags);
                    }
                    case DISLIKE -> {
                        dislikedPlatforms.add(platform);
                        dislikedTags.addAll(tags);
                    }
                    case BLOCK_SOURCE -> {
                        blockedPlatforms.add(platform);
                        dislikedPlatforms.add(platform);
                        dislikedTags.addAll(tags);
                    }
                    case NONE -> {
                    }
                }
            });
        }

        return new FeedbackSignals(likedPlatforms, dislikedPlatforms, blockedPlatforms, likedTags, dislikedTags);
    }

    private RecommendationTodayResponse.Item toTodayItem(RecommendationResponse recommendation, List<String> profileTerms) {
        RecommendationResponse.Content content = recommendation.content();
        return new RecommendationTodayResponse.Item(
                recommendation.id(),
                content.id(),
                content.platform(),
                content.title(),
                content.url(),
                content.author(),
                content.summary(),
                content.publishedAt(),
                content.tags(),
                recommendation.score(),
                recommendation.reason(),
                recommendation.feedback(),
                matchedTags(content, profileTerms),
                new RecommendationTodayResponse.Actions(
                        "/api/v1/recommendations/" + recommendation.id() + "/click",
                        "/api/v1/recommendations/" + recommendation.id() + "/not-interested",
                        "/api/v1/recommendations/" + recommendation.id() + "/feedback"));
    }

    private List<String> matchedTags(RecommendationResponse.Content content, List<String> profileTerms) {
        String haystack = normalize(String.join(" ",
                content.title() == null ? "" : content.title(),
                content.summary() == null ? "" : content.summary(),
                String.join(" ", content.tags())));
        return profileTerms.stream()
                .filter(term -> !normalize(term).isBlank())
                .filter(term -> haystack.contains(normalize(term)))
                .limit(8)
                .toList();
    }

    private String summary(String userId, List<RecommendationTodayResponse.Item> items, String profileSummary) {
        if (items.isEmpty()) {
            return "用户 " + userId + " 暂无今日推荐。建议先上传浏览、搜索、观看或收藏行为，再刷新推荐。";
        }
        String platforms = items.stream()
                .map(RecommendationTodayResponse.Item::platform)
                .filter(platform -> platform != null && !platform.isBlank())
                .map(this::normalize)
                .distinct()
                .limit(4)
                .collect(Collectors.joining("、"));
        String prefix = "今日为用户 " + userId + " 生成 " + items.size() + " 条推荐";
        if (!platforms.isBlank()) {
            prefix += "，主要来源包括 " + platforms;
        }
        if (profileSummary == null || profileSummary.isBlank()) {
            return prefix + "。";
        }
        return prefix + "。画像摘要：" + profileSummary;
    }

    private void recordFeedbackActivity(Recommendation recommendation, FeedbackType feedback) {
        if (feedback == null || feedback == FeedbackType.NONE) {
            return;
        }
        contentItemRepository.findById(recommendation.getContentItemId()).ifPresent(content -> {
            ActivityType type = switch (feedback) {
                case LIKE -> ActivityType.LIKE;
                case FAVORITE -> ActivityType.FAVORITE;
                case DISLIKE, BLOCK_SOURCE -> ActivityType.DISLIKE;
                case READ -> ActivityType.VISIT;
                case NONE -> ActivityType.VISIT;
            };
            activityService.record(new ActivityRequest(
                    recommendation.getUserId(),
                    type,
                    content.getPlatform(),
                    content.getTitle(),
                    content.getUrl(),
                    String.join(" ", content.getTitle() == null ? "" : content.getTitle(),
                    content.getSummary() == null ? "" : content.getSummary()),
                    LocalDateTime.now(),
                    content.getTags(),
                    "HIGH",
                    "PUBLIC_URL",
                    "manual-input",
                    "user_input",
                    content.getPlatform()));
        });
    }

    private int refreshHours(long recentActivityCount, AssistantProperties.RecommendationRefresh config) {
        if (recentActivityCount >= config.getHighActivityThreshold()) {
            return Math.max(1, config.getHighActivityHours());
        }
        if (recentActivityCount >= config.getMediumActivityThreshold()) {
            return Math.max(1, config.getMediumActivityHours());
        }
        return Math.max(1, config.getLowActivityHours());
    }

    private String haystack(ContentItem content) {
        return String.join(" ",
                content.getTitle() == null ? "" : content.getTitle(),
                content.getSummary() == null ? "" : content.getSummary(),
                String.join(" ", content.getTags())).toLowerCase(Locale.ROOT);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private RecommendationResponse toResponse(Recommendation recommendation) {
        ContentItem content = contentItemRepository.findById(recommendation.getContentItemId())
                .orElse(null);
        if (content == null) {
            log.warn("Skip dangling recommendation id={} userId={} contentItemId={}",
                    recommendation.getId(), recommendation.getUserId(), recommendation.getContentItemId());
            return null;
        }
        return new RecommendationResponse(
                recommendation.getId(),
                recommendation.getRecommendationDate(),
                recommendation.getScore(),
                recommendation.getReason(),
                recommendation.getFeedback(),
                new RecommendationResponse.Content(
                        content.getId(),
                        content.getPlatform(),
                        content.getTitle(),
                        content.getUrl(),
                        content.getAuthor(),
                        content.getSummary(),
                        content.getPublishedAt(),
                        content.getTags()));
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private RecommendationTodaySummaryResponse emptyTodaySummary(String userId, String message,
            RecommendationRefreshPolicyResponse policy) {
        log.info("todaySummary empty userId={} behaviorCount24h={} recommendationCount={} status={} fallbackReason={}",
                userId, policy == null ? 0 : policy.recentActivityCount(), 0, "EMPTY", "no_behavior_or_recommendations");
        return new RecommendationTodaySummaryResponse(
                userId,
                LocalDate.now(),
                policy == null ? null : policy.refreshHours(),
                policy == null ? 0 : policy.recentActivityCount(),
                List.of(),
                message,
                "EMPTY");
    }

    private RecommendationResponse basicRecommendation(String userId, long behaviorCount24h) {
        List<UserActivity> activities = userActivityRepository.findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
                userId, LocalDateTime.now().minusHours(24));
        List<UserActivity> trustedActivities = activities.stream()
                .filter(activity -> !"LOW".equalsIgnoreCase(activity.getConfidence())
                        || !"APP_USAGE_SNAPSHOT".equalsIgnoreCase(activity.getDataLevel()))
                .toList();
        List<String> keywords = activities.stream()
                .flatMap(activity -> activity.getTags().stream())
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .filter(value -> !Set.of("local-terminal", "visible-window", "agent-task", "agent-reach").contains(value))
                .distinct()
                .limit(5)
                .toList();
        if (keywords.isEmpty()) {
            keywords = List.of("app-usage", "browser");
        }
        boolean hasXhsBrowserHistory = trustedActivities.stream().anyMatch(activity ->
                normalize(activity.getPlatform()).contains("xiaohongshu")
                        && activity.getTags().stream().map(this::normalize).anyMatch("browser-history"::equals));
        boolean hasXhsLowSignal = activities.stream().anyMatch(activity ->
                normalize(activity.getPlatform()).contains("xiaohongshu")
                        || activity.getTags().stream().map(this::normalize).anyMatch(tag -> tag.contains("xiaohongshu")));
        String title = hasXhsBrowserHistory
                ? "根据你访问的小红书页面推荐相似主题"
                : hasXhsLowSignal ? "小红书生活方式内容探索" : "根据近期访问行为生成的推荐";
        String reason = hasXhsBrowserHistory
                ? "检测到你今天通过浏览器访问过小红书相关页面，因此推荐相似主题内容。"
                : hasXhsLowSignal
                        ? "检测到你今天可能使用过小红书相关窗口，但当前只有低置信度窗口快照，因此先推荐生活方式、经验分享类泛主题。"
                        : "检测到你最近有 " + behaviorCount24h + " 条访问行为，因此根据高频平台和关键词生成基础推荐。";
        return new RecommendationResponse(
                null,
                LocalDate.now(),
                1.0,
                reason,
                FeedbackType.NONE,
                new RecommendationResponse.Content(
                        null,
                        hasXhsLowSignal ? "xiaohongshu" : "local-interest",
                        title,
                        "",
                        "Habit Assistant",
                        reason,
                        LocalDateTime.now(),
                        keywords));
    }

    private record ScoredContent(ContentItem content, double score, String reason) {
    }

    private record FeedbackSignals(
            Set<String> likedPlatforms,
            Set<String> dislikedPlatforms,
            Set<String> blockedPlatforms,
            Set<String> likedTags,
            Set<String> dislikedTags) {
    }
}
