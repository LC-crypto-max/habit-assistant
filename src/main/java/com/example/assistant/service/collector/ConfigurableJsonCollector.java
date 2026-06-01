package com.example.assistant.service.collector;

import com.example.assistant.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ConfigurableJsonCollector implements PlatformCollector {

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ConfigurableJsonCollector(AssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "habit-assistant-java/0.0.1")
                .build();
    }

    @Override
    public String platform() {
        return "json-api";
    }

    @Override
    public List<CollectedContent> search(String userId, List<String> interestTerms) {
        AssistantProperties.JsonApi jsonApi = properties.getCollectors().getJsonApi();
        if (!jsonApi.isEnabled() || jsonApi.getSources().isEmpty()) {
            return List.of();
        }
        List<CollectedContent> results = new ArrayList<>();
        for (AssistantProperties.JsonSource source : jsonApi.getSources()) {
            for (String term : interestTerms) {
                results.addAll(fetchSource(source, term));
            }
        }
        return results;
    }

    private List<CollectedContent> fetchSource(AssistantProperties.JsonSource source, String term) {
        if (source.getEndpointTemplate() == null || source.getEndpointTemplate().isBlank()) {
            return List.of();
        }
        try {
            String url = source.getEndpointTemplate()
                    .replace("{query}", encode(term))
                    .replace("{keyword}", encode(term));
            var request = restClient.get().uri(url);
            for (var header : source.getHeaders().entrySet()) {
                request = request.header(header.getKey(), header.getValue());
            }
            String body = request.retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.at(source.getResultsPointer());
            if (!items.isArray()) {
                return List.of();
            }
            List<CollectedContent> results = new ArrayList<>();
            for (JsonNode item : items) {
                String title = textAt(item, source.getTitlePointer());
                String urlValue = textAt(item, source.getUrlPointer());
                if (title.isBlank() || urlValue.isBlank()) {
                    continue;
                }
                String summary = textAt(item, source.getSummaryPointer());
                results.add(new CollectedContent(
                        source.getPlatform(),
                        "json-" + Integer.toHexString((source.getPlatform() + urlValue).hashCode()),
                        title,
                        urlValue,
                        textAt(item, source.getAuthorPointer()),
                        summary,
                        parseDate(textAt(item, source.getPublishedAtPointer())),
                        source.getTags()));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String textAt(JsonNode node, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return "";
        }
        JsonNode value = node.at(pointer);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
    }
}
