package com.example.assistant.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String platform;

    @Column(length = 200)
    private String externalId;

    @Column(length = 512)
    private String title;

    @Column(length = 2048)
    private String url;

    @Column(length = 300)
    private String author;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private LocalDateTime publishedAt;

    private LocalDateTime collectedAt;

    @Column(length = 64, unique = true)
    private String contentHash;

    @ElementCollection
    private List<String> tags = new ArrayList<>();

    protected ContentItem() {
    }

    public ContentItem(String platform, String externalId, String title, String url, String author, String summary,
            LocalDateTime publishedAt, LocalDateTime collectedAt, String contentHash, List<String> tags) {
        this.platform = platform;
        this.externalId = externalId;
        this.title = title;
        this.url = url;
        this.author = author;
        this.summary = summary;
        this.publishedAt = publishedAt;
        this.collectedAt = collectedAt;
        this.contentHash = contentHash;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public Long getId() {
        return id;
    }

    public String getPlatform() {
        return platform;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getAuthor() {
        return author;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public List<String> getTags() {
        return tags;
    }
}
