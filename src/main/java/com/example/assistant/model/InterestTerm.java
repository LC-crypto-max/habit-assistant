package com.example.assistant.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_interest_user_term", columnNames = {"user_id", "term"}))
public class InterestTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "term")
    private String term;

    private double weight;

    private int hitCount;

    private LocalDateTime lastSeenAt;

    protected InterestTerm() {
    }

    public InterestTerm(String userId, String term, double weight, LocalDateTime lastSeenAt) {
        this.userId = userId;
        this.term = term;
        this.weight = weight;
        this.hitCount = 1;
        this.lastSeenAt = lastSeenAt;
    }

    public void reinforce(double delta, LocalDateTime seenAt) {
        this.weight += delta;
        this.hitCount += 1;
        this.lastSeenAt = seenAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTerm() {
        return term;
    }

    public double getWeight() {
        return weight;
    }

    public int getHitCount() {
        return hitCount;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }
}
