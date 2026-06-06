package com.example.assistant.service;

import com.example.assistant.codexagent.repository.DailyInterestProfileRepository;
import com.example.assistant.codexagent.repository.DailyRecommendationRepository;
import com.example.assistant.codexagent.repository.InterestEventRepository;
import com.example.assistant.dto.ResetDataResponse;
import com.example.assistant.repo.AgentQueryTaskRepository;
import com.example.assistant.repo.ContentItemRepository;
import com.example.assistant.repo.InterestTermRepository;
import com.example.assistant.repo.RecommendationRepository;
import com.example.assistant.repo.UserActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDataResetService {

    private final UserActivityRepository userActivityRepository;
    private final InterestTermRepository interestTermRepository;
    private final RecommendationRepository recommendationRepository;
    private final ContentItemRepository contentItemRepository;
    private final AgentQueryTaskRepository agentQueryTaskRepository;
    private final InterestEventRepository interestEventRepository;
    private final DailyInterestProfileRepository dailyInterestProfileRepository;
    private final DailyRecommendationRepository dailyRecommendationRepository;
    private final ObjectMapper objectMapper;

    public AdminDataResetService(UserActivityRepository userActivityRepository,
            InterestTermRepository interestTermRepository, RecommendationRepository recommendationRepository,
            ContentItemRepository contentItemRepository, AgentQueryTaskRepository agentQueryTaskRepository,
            InterestEventRepository interestEventRepository,
            DailyInterestProfileRepository dailyInterestProfileRepository,
            DailyRecommendationRepository dailyRecommendationRepository, ObjectMapper objectMapper) {
        this.userActivityRepository = userActivityRepository;
        this.interestTermRepository = interestTermRepository;
        this.recommendationRepository = recommendationRepository;
        this.contentItemRepository = contentItemRepository;
        this.agentQueryTaskRepository = agentQueryTaskRepository;
        this.interestEventRepository = interestEventRepository;
        this.dailyInterestProfileRepository = dailyInterestProfileRepository;
        this.dailyRecommendationRepository = dailyRecommendationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResetDataResponse resetDevData() {
        Path backupDir = Path.of("data", "backups",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        try {
            Files.createDirectories(backupDir);
            backupTables(backupDir);
            backupJsonDirectories(backupDir);
        } catch (IOException exception) {
            throw new IllegalStateException("备份失败，已取消清理：" + exception.getMessage(), exception);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("userActivities", userActivityRepository.count());
        counts.put("interestTerms", interestTermRepository.count());
        counts.put("recommendations", recommendationRepository.count());
        counts.put("contentItems", contentItemRepository.count());
        counts.put("agentQueryTasks", agentQueryTaskRepository.count());
        counts.put("interestEvents", interestEventRepository.count());
        counts.put("dailyProfiles", dailyInterestProfileRepository.count());
        counts.put("dailyRecommendations", dailyRecommendationRepository.count());

        recommendationRepository.deleteAllInBatch();
        contentItemRepository.deleteAllInBatch();
        interestTermRepository.deleteAllInBatch();
        userActivityRepository.deleteAllInBatch();
        agentQueryTaskRepository.deleteAllInBatch();
        interestEventRepository.deleteAllInBatch();
        dailyInterestProfileRepository.deleteAllInBatch();
        dailyRecommendationRepository.deleteAllInBatch();

        return new ResetDataResponse(true, backupDir.toAbsolutePath().toString(), counts,
                "已备份并清理开发测试数据，表结构未删除。");
    }

    private void backupTables(Path backupDir) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("user-activities.json").toFile(), userActivityRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("interest-terms.json").toFile(), interestTermRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("recommendations.json").toFile(), recommendationRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("content-items.json").toFile(), contentItemRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("agent-query-tasks.json").toFile(), agentQueryTaskRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("codex-interest-events.json").toFile(), interestEventRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("codex-daily-profiles.json").toFile(), dailyInterestProfileRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupDir.resolve("codex-daily-recommendations.json").toFile(), dailyRecommendationRepository.findAll());
    }

    private void backupJsonDirectories(Path backupDir) throws IOException {
        for (String name : List.of("processed", "profiles", "recommendations")) {
            Path source = Path.of("data", name);
            if (Files.exists(source)) {
                moveDirectory(source, backupDir.resolve(name));
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.move(source, target);
    }
}
