package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.BrowserVisitEventDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.profile.TagExtractor;
import com.example.assistant.codexagent.sanitizer.PrivacySanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class PublicUrlMetadataCollector implements DataCollector {

    private final CodexAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final DataPolicyChecker policyChecker;
    private final PrivacySanitizer sanitizer;
    private final TagExtractor tagExtractor;

    public PublicUrlMetadataCollector(CodexAgentProperties properties, ObjectMapper objectMapper,
            DataPolicyChecker policyChecker, PrivacySanitizer sanitizer, TagExtractor tagExtractor) {
        this.properties = properties;
        this.objectMapper = objectMapper.findAndRegisterModules();
        this.policyChecker = policyChecker;
        this.sanitizer = sanitizer;
        this.tagExtractor = tagExtractor;
    }

    @Override
    public boolean supports(String source) {
        return DataAccessScope.PUBLIC_URL_METADATA.name().equals(source);
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
            int max = Math.min(properties.getLimits().getMaxUrlPerTask(),
                    properties.getSources().getPublicUrlMetadata().getMaxUrlsPerTask());
            for (BrowserVisitEventDTO row : rows.stream().limit(max).toList()) {
                if (!policyChecker.checkDomain(row.url(), authorization.getAllowedDomains()).allowed()) {
                    continue;
                }
                InterestEventDTO event = fetch(request, row.url());
                if (event != null) {
                    events.add(event);
                }
            }
            return events;
        } catch (Exception e) {
            return List.of();
        }
    }

    private InterestEventDTO fetch(AgentTaskRequest request, String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("habit-assistant-java/0.0.1")
                    .ignoreContentType(true)
                    .timeout(3000)
                    .get();
            String title = sanitizer.sanitize(doc.title());
            String description = sanitizer.sanitize(doc.selectFirst("meta[name=description]") == null
                    ? ""
                    : doc.selectFirst("meta[name=description]").attr("content"));
            String keywordsRaw = sanitizer.sanitize(doc.selectFirst("meta[name=keywords]") == null
                    ? ""
                    : doc.selectFirst("meta[name=keywords]").attr("content"));
            List<String> keywords = Arrays.stream(keywordsRaw.split("[,，;；]"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
            List<String> tags = new ArrayList<>(tagExtractor.extract(title + " " + description + " " + keywordsRaw));
            tags.addAll(keywords.stream().limit(5).toList());
            return new InterestEventDTO(
                    UUID.randomUUID().toString(),
                    request.userId(),
                    DataAccessScope.PUBLIC_URL_METADATA.name(),
                    "PUBLIC_WEB",
                    "PUBLIC_URL_METADATA",
                    title,
                    url,
                    "",
                    tags,
                    description,
                    OffsetDateTime.now(),
                    1.0,
                    Map.of("keywords", keywords));
        } catch (Exception e) {
            return null;
        }
    }
}
