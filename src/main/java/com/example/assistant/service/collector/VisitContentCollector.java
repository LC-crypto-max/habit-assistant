package com.example.assistant.service.collector;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.UserActivityRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class VisitContentCollector implements PlatformCollector {

    private final AssistantProperties properties;
    private final UserActivityRepository activityRepository;

    public VisitContentCollector(AssistantProperties properties, UserActivityRepository activityRepository) {
        this.properties = properties;
        this.activityRepository = activityRepository;
    }

    @Override
    public String platform() {
        return "visited";
    }

    @Override
    public List<CollectedContent> search(String userId, List<String> interestTerms) {
        String resolvedUserId = userId == null || userId.isBlank() ? properties.getUserId() : userId;
        return activityRepository.findTop100ByUserIdOrderByOccurredAtDesc(resolvedUserId)
                .stream()
                .filter(activity -> activity.getUrl() != null && activity.getUrl().startsWith("http"))
                .filter(activity -> matches(activity, interestTerms))
                .map(activity -> new CollectedContent(
                        activity.getPlatform() == null ? "visited" : activity.getPlatform(),
                        "visit-" + activity.getId(),
                        activity.getTitle(),
                        activity.getUrl(),
                        "访问记录",
                        activity.getText(),
                        activity.getOccurredAt(),
                        activity.getTags()))
                .toList();
    }

    private boolean matches(UserActivity activity, List<String> interestTerms) {
        if (interestTerms == null || interestTerms.isEmpty()) {
            return true;
        }
        String haystack = String.join(" ",
                activity.getTitle() == null ? "" : activity.getTitle(),
                activity.getText() == null ? "" : activity.getText(),
                activity.getPlatform() == null ? "" : activity.getPlatform(),
                String.join(" ", activity.getTags())).toLowerCase(Locale.ROOT);
        return interestTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(haystack::contains);
    }
}
