package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.BrowserVisitEventDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.profile.TagExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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
public class BrowserHistoryCollector implements DataCollector {

    private final CodexAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final DataPolicyChecker policyChecker;
    private final TagExtractor tagExtractor;

    public BrowserHistoryCollector(CodexAgentProperties properties, ObjectMapper objectMapper,
            DataPolicyChecker policyChecker, TagExtractor tagExtractor) {
        this.properties = properties;
        this.objectMapper = objectMapper.findAndRegisterModules();
        this.policyChecker = policyChecker;
        this.tagExtractor = tagExtractor;
    }

    @Override
    public boolean supports(String source) {
        return DataAccessScope.BROWSER_HISTORY.name().equals(source);
    }

    @Override
    public List<InterestEventDTO> collect(AgentTaskRequest request, UserDataAuthorization authorization) {
        Path file = Path.of(properties.getSources().getBrowserHistory().getSampleFile());
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<BrowserVisitEventDTO> rows = objectMapper.readValue(file.toFile(),
                    new TypeReference<List<BrowserVisitEventDTO>>() {
                    });
            List<InterestEventDTO> events = new ArrayList<>();
            for (BrowserVisitEventDTO row : rows) {
                if (!policyChecker.checkDomain(row.url(), authorization.getAllowedDomains()).allowed()) {
                    continue;
                }
                String platform = platform(row.url());
                double weight = Math.min(5.0, 0.8 + Math.max(1, row.visitCount()) * 0.25);
                List<String> tags = tagExtractor.extract(row.title() + " " + row.url() + " " + platform);
                events.add(new InterestEventDTO(
                        UUID.randomUUID().toString(),
                        request.userId(),
                        DataAccessScope.BROWSER_HISTORY.name(),
                        platform,
                        "BROWSER_VISIT",
                        row.title(),
                        row.url(),
                        "",
                        tags,
                        "用户访问了公开网页：" + row.title(),
                        row.visitTime() == null ? OffsetDateTime.now() : row.visitTime(),
                        weight,
                        Map.of("visitCount", row.visitCount())));
            }
            return events;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String platform(String url) {
        try {
            String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
            if (host.contains("youtube")) return "YOUTUBE";
            if (host.contains("bilibili")) return "BILIBILI";
            if (host.contains("xiaohongshu")) return "XIAOHONGSHU";
            if (host.contains("douyin")) return "DOUYIN";
            if (host.contains("zhihu")) return "ZHIHU";
            if (host.contains("github")) return "GITHUB";
            if (host.contains("csdn")) return "CSDN";
            if (host.contains("juejin")) return "JUEJIN";
            if (host.contains("weixin") || host.contains("qq.com")) return "WECHAT";
            return host.toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "WEB";
        }
    }
}
