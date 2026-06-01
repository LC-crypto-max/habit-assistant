package com.example.assistant.service.collector;

import com.example.assistant.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class XRecentSearchCollector implements PlatformCollector {

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public XRecentSearchCollector(AssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.twitter.com/2")
                .defaultHeader("User-Agent", "habit-assistant-java/0.0.1")
                .build();
    }

    @Override
    public String platform() {
        return "x";
    }

    @Override
    public List<CollectedContent> search(String userId, List<String> interestTerms) {
        AssistantProperties.X x = properties.getCollectors().getX();
        if (!x.isEnabled() || x.getBearerToken() == null || x.getBearerToken().isBlank()) {
            return List.of();
        }
        List<CollectedContent> results = new ArrayList<>();
        for (String term : interestTerms) {
            results.addAll(searchTerm(term, x));
        }
        return results;
    }

    private List<CollectedContent> searchTerm(String term, AssistantProperties.X x) {
        try {
            int maxResults = Math.max(10, Math.min(100, x.getMaxResults()));
            String body = restClient.get()
                    .uri("/tweets/search/recent?query={query}&max_results={maxResults}&tweet.fields=created_at,author_id,lang",
                            term + " -is:retweet", maxResults)
                    .header("Authorization", "Bearer " + x.getBearerToken())
                    .retrieve()
                    .body(String.class);
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<CollectedContent> results = new ArrayList<>();
            for (JsonNode tweet : data) {
                String id = tweet.path("id").asText();
                String text = tweet.path("text").asText();
                if (id.isBlank() || text.isBlank()) {
                    continue;
                }
                results.add(new CollectedContent(
                        "x",
                        id,
                        text.length() > 80 ? text.substring(0, 80) : text,
                        "https://x.com/i/web/status/" + id,
                        tweet.path("author_id").asText(""),
                        text,
                        parseDate(tweet.path("created_at").asText()),
                        List.of("X", "Twitter", term)));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
