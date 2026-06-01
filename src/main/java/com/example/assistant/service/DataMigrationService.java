package com.example.assistant.service;

import com.example.assistant.dto.ActivityBackupResponse;
import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ContentItemBackupRecord;
import com.example.assistant.dto.FullBackupResponse;
import com.example.assistant.dto.ImportBackupResponse;
import com.example.assistant.dto.ImportFullBackupResponse;
import com.example.assistant.dto.RecommendationBackupRecord;
import com.example.assistant.model.FeedbackType;
import com.example.assistant.model.ContentItem;
import com.example.assistant.model.Recommendation;
import com.example.assistant.model.UserActivity;
import com.example.assistant.repo.ContentItemRepository;
import com.example.assistant.repo.RecommendationRepository;
import com.example.assistant.repo.UserActivityRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataMigrationService {

    private final UserActivityRepository activityRepository;
    private final ActivityService activityService;
    private final ContentItemRepository contentItemRepository;
    private final RecommendationRepository recommendationRepository;

    public DataMigrationService(UserActivityRepository activityRepository, ActivityService activityService,
            ContentItemRepository contentItemRepository, RecommendationRepository recommendationRepository) {
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.contentItemRepository = contentItemRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional(readOnly = true)
    public ActivityBackupResponse exportActivities() {
        List<ActivityRequest> activities = activityRepository.findAll().stream()
                .map(this::toRequest)
                .toList();
        return new ActivityBackupResponse(activities.size(), activities);
    }

    @Transactional
    public ImportBackupResponse importActivities(ActivityBackupResponse backup) {
        if (backup == null || backup.activities() == null) {
            return new ImportBackupResponse(0);
        }
        int imported = 0;
        for (ActivityRequest activity : backup.activities()) {
            activityService.record(activity);
            imported++;
        }
        return new ImportBackupResponse(imported);
    }

    @Transactional(readOnly = true)
    public FullBackupResponse exportFull() {
        ActivityBackupResponse activities = exportActivities();
        List<ContentItemBackupRecord> contents = contentItemRepository.findAll().stream()
                .map(this::toContentRecord)
                .toList();
        List<RecommendationBackupRecord> recommendations = recommendationRepository.findAll().stream()
                .map(this::toRecommendationRecord)
                .toList();
        return new FullBackupResponse(
                activities,
                contents.size(),
                contents,
                recommendations.size(),
                recommendations);
    }

    @Transactional
    public ImportFullBackupResponse importFull(FullBackupResponse backup) {
        if (backup == null) {
            return new ImportFullBackupResponse(0, 0, 0);
        }
        int activitiesImported = importActivities(backup.activities()).imported();
        Map<Long, Long> contentIdMap = new HashMap<>();
        int contentsImported = 0;
        if (backup.contents() != null) {
            for (ContentItemBackupRecord record : backup.contents()) {
                if (record == null || record.contentHash() == null || record.contentHash().isBlank()) {
                    continue;
                }
                boolean existed = contentItemRepository.findByContentHash(record.contentHash()).isPresent();
                ContentItem content = contentItemRepository.findByContentHash(record.contentHash())
                        .orElseGet(() -> contentItemRepository.save(new ContentItem(
                                record.platform(),
                                record.externalId(),
                                record.title(),
                                record.url(),
                                record.author(),
                                record.summary(),
                                record.publishedAt(),
                                record.collectedAt(),
                                record.contentHash(),
                                record.tags())));
                contentIdMap.put(record.originalId(), content.getId());
                if (!existed) {
                    contentsImported++;
                }
            }
        }

        int recommendationsImported = 0;
        if (backup.recommendations() != null) {
            for (RecommendationBackupRecord record : backup.recommendations()) {
                if (record == null || record.userId() == null || record.recommendationDate() == null) {
                    continue;
                }
                Long contentId = contentIdMap.get(record.contentOriginalId());
                if (contentId == null) {
                    continue;
                }
                if (recommendationRepository.existsByUserIdAndRecommendationDateAndContentItemId(
                        record.userId(), record.recommendationDate(), contentId)) {
                    continue;
                }
                Recommendation recommendation = new Recommendation(
                        record.userId(),
                        contentId,
                        record.recommendationDate(),
                        record.score(),
                        record.reason(),
                        record.createdAt());
                recommendation.updateFeedback(record.feedback() == null ? FeedbackType.NONE : record.feedback());
                recommendationRepository.save(recommendation);
                recommendationsImported++;
            }
        }
        return new ImportFullBackupResponse(activitiesImported, contentsImported, recommendationsImported);
    }

    private ActivityRequest toRequest(UserActivity activity) {
        return new ActivityRequest(
                activity.getUserId(),
                activity.getType(),
                activity.getPlatform(),
                activity.getTitle(),
                activity.getUrl(),
                activity.getText(),
                activity.getOccurredAt(),
                activity.getTags());
    }

    private ContentItemBackupRecord toContentRecord(ContentItem content) {
        return new ContentItemBackupRecord(
                content.getId(),
                content.getPlatform(),
                content.getExternalId(),
                content.getTitle(),
                content.getUrl(),
                content.getAuthor(),
                content.getSummary(),
                content.getPublishedAt(),
                content.getCollectedAt(),
                content.getContentHash(),
                content.getTags());
    }

    private RecommendationBackupRecord toRecommendationRecord(Recommendation recommendation) {
        return new RecommendationBackupRecord(
                recommendation.getUserId(),
                Objects.requireNonNull(recommendation.getContentItemId()),
                recommendation.getRecommendationDate(),
                recommendation.getScore(),
                recommendation.getReason(),
                recommendation.getFeedback(),
                recommendation.getCreatedAt());
    }
}
