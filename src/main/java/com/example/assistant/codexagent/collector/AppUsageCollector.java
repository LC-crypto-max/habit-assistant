package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.AppUsageEventDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.example.assistant.codexagent.profile.TagExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AppUsageCollector implements DataCollector {

    private final CodexAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final TagExtractor tagExtractor;

    public AppUsageCollector(CodexAgentProperties properties, ObjectMapper objectMapper, TagExtractor tagExtractor) {
        this.properties = properties;
        this.objectMapper = objectMapper.findAndRegisterModules();
        this.tagExtractor = tagExtractor;
    }

    @Override
    public boolean supports(String source) {
        return DataAccessScope.APP_USAGE_SUMMARY.name().equals(source);
    }

    @Override
    public List<InterestEventDTO> collect(AgentTaskRequest request, UserDataAuthorization authorization) {
        Path file = Path.of(properties.getSources().getAppUsageSummary().getSampleFile());
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<AppUsageEventDTO> rows = objectMapper.readValue(file.toFile(),
                    new TypeReference<List<AppUsageEventDTO>>() {
                    });
            List<InterestEventDTO> events = new ArrayList<>();
            for (AppUsageEventDTO row : rows) {
                if (!authorization.getAllowedApps().contains(row.packageName())) {
                    continue;
                }
                String platform = platform(row.packageName(), row.appName());
                long minutes = Math.max(1, row.totalTimeInForegroundSeconds() / 60);
                double weight = Math.min(5.0, 0.5 + minutes / 60.0 + row.launchCount() * 0.1);
                String title = "今日使用 " + row.appName() + " " + minutes + " 分钟";
                String summary = "用户今日多次使用 " + row.appName() + "，可能关注" + category(platform) + "。";
                events.add(new InterestEventDTO(
                        UUID.randomUUID().toString(),
                        request.userId(),
                        DataAccessScope.APP_USAGE_SUMMARY.name(),
                        platform,
                        "APP_USAGE",
                        title,
                        "",
                        "",
                        tagExtractor.extract(row.appName() + " " + platform + " " + category(platform)),
                        summary,
                        row.lastTimeUsed() == null ? OffsetDateTime.now() : row.lastTimeUsed(),
                        weight,
                        Map.of(
                                "packageName", row.packageName(),
                                "totalTimeInForegroundSeconds", row.totalTimeInForegroundSeconds(),
                                "launchCount", row.launchCount())));
            }
            return events;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String platform(String packageName, String appName) {
        String text = (packageName + " " + appName).toLowerCase(Locale.ROOT);
        if (text.contains("youtube")) return "YOUTUBE";
        if (text.contains("bili") || text.contains("danmaku")) return "BILIBILI";
        if (text.contains("xingin") || text.contains("xhs")) return "XIAOHONGSHU";
        if (text.contains("tencent.mm") || text.contains("wechat")) return "WECHAT";
        if (text.contains("aweme") || text.contains("douyin")) return "DOUYIN";
        if (text.contains("github")) return "GITHUB";
        return appName == null ? "APP" : appName.toUpperCase(Locale.ROOT);
    }

    private String category(String platform) {
        return switch (platform) {
            case "YOUTUBE", "BILIBILI" -> "视频学习或娱乐内容";
            case "WECHAT" -> "公众号或社交资讯";
            case "XIAOHONGSHU" -> "生活方式或消费兴趣";
            case "DOUYIN" -> "短视频或娱乐内容";
            case "GITHUB" -> "技术学习";
            default -> "综合兴趣";
        };
    }
}
