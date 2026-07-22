package com.example.assistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String userId;

    private Long contentItemId;

    private LocalDate recommendationDate;

    private double score;

    @Column(length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    private FeedbackType feedback = FeedbackType.NONE;

    private LocalDateTime createdAt;

    protected Recommendation() {
    }

    public Recommendation(String userId, Long contentItemId, LocalDate recommendationDate, double score, String reason,
            LocalDateTime createdAt) {
        this.userId = userId;
        this.contentItemId = contentItemId;
        this.recommendationDate = recommendationDate;
        this.score = score;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void updateFeedback(FeedbackType feedback) {
        this.feedback = feedback;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getContentItemId() {
        return contentItemId;
    }

    public LocalDate getRecommendationDate() {
        return recommendationDate;
    }

    public double getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public FeedbackType getFeedback() {
        return feedback;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
