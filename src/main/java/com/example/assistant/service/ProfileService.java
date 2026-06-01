package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.InterestTermResponse;
import com.example.assistant.dto.ProfileResponse;
import com.example.assistant.dto.UserSummaryResponse;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.InterestTerm;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.InterestTermRepository;
import com.example.assistant.repo.UserActivityRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final AssistantProperties properties;
    private final InterestTermRepository interestTermRepository;
    private final UserActivityRepository activityRepository;
    private final KeywordExtractor keywordExtractor;
    private final UserContext userContext;

    public ProfileService(AssistantProperties properties, InterestTermRepository interestTermRepository,
            UserActivityRepository activityRepository, KeywordExtractor keywordExtractor, UserContext userContext) {
        this.properties = properties;
        this.interestTermRepository = interestTermRepository;
        this.activityRepository = activityRepository;
        this.keywordExtractor = keywordExtractor;
        this.userContext = userContext;
    }

    @Transactional
    public void learnFrom(UserActivity activity) {
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
                        activity.getTags()))
                .toList();
        return new ProfileResponse(resolvedUserId, terms, activities);
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

    private String joinText(UserActivity activity) {
        return String.join(" ",
                activity.getTitle() == null ? "" : activity.getTitle(),
                activity.getText() == null ? "" : activity.getText(),
                activity.getPlatform() == null ? "" : activity.getPlatform());
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
}
