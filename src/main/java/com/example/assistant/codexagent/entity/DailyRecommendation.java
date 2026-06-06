package com.example.assistant.codexagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class DailyRecommendation {

    @Id
    private String recommendationId;
    private String userId;
    private LocalDate recommendationDate;
    @Column(length = 4000)
    private String recommendationJson;

    protected DailyRecommendation() {
    }
}
