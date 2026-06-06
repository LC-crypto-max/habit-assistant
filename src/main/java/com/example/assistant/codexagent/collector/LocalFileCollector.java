package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.policy.PolicyCheckResult;
import com.example.assistant.codexagent.profile.TagExtractor;
import com.example.assistant.codexagent.sanitizer.PrivacySanitizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class LocalFileCollector implements DataCollector {

    private final CodexAgentProperties properties;
    private final DataPolicyChecker policyChecker;
    private final PrivacySanitizer sanitizer;
    private final TagExtractor tagExtractor;

    public LocalFileCollector(CodexAgentProperties properties, DataPolicyChecker policyChecker,
            PrivacySanitizer sanitizer, TagExtractor tagExtractor) {
        this.properties = properties;
        this.policyChecker = policyChecker;
        this.sanitizer = sanitizer;
        this.tagExtractor = tagExtractor;
    }

    @Override
    public boolean supports(String source) {
        return DataAccessScope.LOCAL_NOTES.name().equals(source);
    }

    @Override
    public List<InterestEventDTO> collect(AgentTaskRequest request, UserDataAuthorization authorization) {
        List<InterestEventDTO> events = new ArrayList<>();
        long maxBytes = properties.getLimits().getMaxFileSizeMb() * 1024L * 1024L;
        for (String allowedPath : authorization.getAllowedPaths()) {
            PolicyCheckResult pathCheck = policyChecker.checkPath(allowedPath, authorization.getAllowedPaths());
            if (!pathCheck.allowed()) {
                continue;
            }
            Path root = Path.of(allowedPath);
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root, 4)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> allowedExtension(path.toString()))
                        .filter(path -> safeSize(path, maxBytes))
                        .forEach(path -> readNote(request, events, path));
            } catch (Exception ignored) {
            }
        }
        return events;
    }

    private void readNote(AgentTaskRequest request, List<InterestEventDTO> events, Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String text = sanitizer.sanitize(raw);
            String title = firstLine(text, path.getFileName().toString());
            String summary = text.length() > 160 ? text.substring(0, 160) : text;
            events.add(new InterestEventDTO(
                    UUID.randomUUID().toString(),
                    request.userId(),
                    DataAccessScope.LOCAL_NOTES.name(),
                    "LOCAL_NOTES",
                    "LOCAL_NOTE",
                    title,
                    path.toString(),
                    "",
                    tagExtractor.extract(text),
                    summary,
                    OffsetDateTime.now(),
                    1.5,
                    Map.of("path", path.toString())));
        } catch (Exception ignored) {
        }
    }

    private boolean allowedExtension(String value) {
        String lower = value.toLowerCase();
        return properties.getSources().getLocalNotes().getAllowedExtensions().stream()
                .anyMatch(lower::endsWith);
    }

    private boolean safeSize(Path path, long maxBytes) {
        try {
            return Files.size(path) <= maxBytes;
        } catch (Exception e) {
            return false;
        }
    }

    private String firstLine(String text, String fallback) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback)
                .replaceFirst("^#+\\s*", "");
    }
}
