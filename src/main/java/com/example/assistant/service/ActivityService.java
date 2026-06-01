package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.SearchTermRequest;
import com.example.assistant.model.ActivityType;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final AssistantProperties properties;
    private final UserActivityRepository activityRepository;
    private final ProfileService profileService;
    private final UserContext userContext;

    public ActivityService(AssistantProperties properties, UserActivityRepository activityRepository,
            ProfileService profileService, UserContext userContext) {
        this.properties = properties;
        this.activityRepository = activityRepository;
        this.profileService = profileService;
        this.userContext = userContext;
    }

    @Transactional
    public ActivityResponse record(ActivityRequest request) {
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        String userId = userContext.resolve(request.userId());
        UserActivity activity = new UserActivity(
                userId,
                request.type(),
                request.platform(),
                request.title(),
                request.url(),
                request.text(),
                occurredAt,
                request.tags());
        UserActivity saved = activityRepository.save(activity);
        profileService.learnFrom(saved);
        return toResponse(saved);
    }

    @Transactional
    public ActivityResponse recordSearchTerm(SearchTermRequest request) {
        return record(new ActivityRequest(
                request.userId(),
                ActivityType.SEARCH,
                request.platform(),
                request.keyword(),
                null,
                request.keyword(),
                request.occurredAt(),
                List.of(request.keyword())));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent() {
        return recent(properties.getUserId());
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(String userId) {
        return activityRepository.findTop30ByUserIdOrderByOccurredAtDesc(userContext.resolve(userId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ActivityResponse toResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getPlatform(),
                activity.getTitle(),
                activity.getUrl(),
                activity.getText(),
                activity.getOccurredAt(),
                activity.getTags());
    }
}
