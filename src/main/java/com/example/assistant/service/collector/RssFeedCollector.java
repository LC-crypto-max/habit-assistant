package com.example.assistant.service.collector;

import com.example.assistant.config.AssistantProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class RssFeedCollector implements PlatformCollector {

    private final AssistantProperties properties;
    private final RestClient restClient;

    public RssFeedCollector(AssistantProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "habit-assistant-java/0.0.1")
                .build();
    }

    @Override
    public String platform() {
        return "rss";
    }

    @Override
    public List<CollectedContent> search(String userId, List<String> interestTerms) {
        AssistantProperties.Rss rss = properties.getCollectors().getRss();
        if (!rss.isEnabled() || rss.getFeeds().isEmpty()) {
            return List.of();
        }
        List<CollectedContent> results = new ArrayList<>();
        for (AssistantProperties.RssFeed feed : rss.getFeeds()) {
            if (feed.getUrl() == null || feed.getUrl().isBlank()) {
                continue;
            }
            try {
                String xml = restClient.get().uri(feed.getUrl()).retrieve().body(String.class);
                results.addAll(parseFeed(feed, xml, interestTerms));
            } catch (RuntimeException e) {
                // A failed external feed should not break local recommendation generation.
            }
        }
        return results;
    }

    private List<CollectedContent> parseFeed(AssistantProperties.RssFeed feed, String xml, List<String> interestTerms) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            List<CollectedContent> items = new ArrayList<>();
            collectNodes(items, feed, document.getElementsByTagName("item"), interestTerms);
            collectNodes(items, feed, document.getElementsByTagName("entry"), interestTerms);
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void collectNodes(List<CollectedContent> items, AssistantProperties.RssFeed feed, NodeList nodes,
            List<String> interestTerms) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String title = firstText(element, "title");
            String link = firstText(element, "link");
            if (link.isBlank()) {
                Element linkElement = firstElement(element, "link");
                link = linkElement == null ? "" : linkElement.getAttribute("href");
            }
            String summary = firstNonBlank(firstText(element, "description"), firstText(element, "summary"),
                    firstText(element, "content"));
            if (title.isBlank() || link.isBlank() || !matches(title + " " + summary + " " + String.join(" ", feed.getTags()), interestTerms)) {
                continue;
            }
            items.add(new CollectedContent(
                    feed.getPlatform(),
                    "rss-" + Integer.toHexString((feed.getUrl() + link).hashCode()),
                    title,
                    link,
                    firstNonBlank(firstText(element, "author"), firstText(element, "creator")),
                    summary,
                    parseDate(firstNonBlank(firstText(element, "pubDate"), firstText(element, "published"),
                            firstText(element, "updated"))),
                    feed.getTags()));
        }
    }

    private boolean matches(String text, List<String> interestTerms) {
        if (interestTerms == null || interestTerms.isEmpty()) {
            return true;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        return interestTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(haystack::contains);
    }

    private String firstText(Element element, String tagName) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0 || list.item(0).getTextContent() == null) {
            return "";
        }
        return list.item(0).getTextContent().trim();
    }

    private Element firstElement(Element element, String tagName) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0) {
            return null;
        }
        return (Element) list.item(0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
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
}
