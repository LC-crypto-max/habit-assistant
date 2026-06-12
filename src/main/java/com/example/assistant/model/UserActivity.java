package com.example.assistant.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private ActivityType type;

    private String platform;

    @Column(length = 512)
    private String title;

    @Column(length = 1024)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String text;

    private LocalDateTime occurredAt;

    private String confidence;

    private String dataLevel;

    private String source;

    private String detectionReason;

    private String matchedKeyword;

    @Column(columnDefinition = "TEXT")
    private String rawEvidence;

    @ElementCollection
    private List<String> tags = new ArrayList<>();

    protected UserActivity() {
    }

    public UserActivity(String userId, ActivityType type, String platform, String title, String url, String text,
            LocalDateTime occurredAt, List<String> tags) {
        this(userId, type, platform, title, url, text, occurredAt, tags, null, null, null, null, null, null);
    }

    public UserActivity(String userId, ActivityType type, String platform, String title, String url, String text,
            LocalDateTime occurredAt, List<String> tags, String confidence, String dataLevel, String source,
            String detectionReason, String matchedKeyword, String rawEvidence) {
        this.userId = userId;
        this.type = type;
        this.platform = platform;
        this.title = title;
        this.url = url;
        this.text = text;
        this.occurredAt = occurredAt;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.confidence = confidence;
        this.dataLevel = dataLevel;
        this.source = source;
        this.detectionReason = detectionReason;
        this.matchedKeyword = matchedKeyword;
        this.rawEvidence = rawEvidence;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public ActivityType getType() {
        return type;
    }

    public String getPlatform() {
        return platform;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public String getSource() {
        return source;
    }

    public String getDetectionReason() {
        return detectionReason;
    }

    public String getMatchedKeyword() {
        return matchedKeyword;
    }

    public String getRawEvidence() {
        return rawEvidence;
    }
}
