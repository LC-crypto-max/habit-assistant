package com.example.assistant.codexagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class DailyInterestProfile {

    @Id
    private String profileId;
    private String userId;
    private LocalDate profileDate;
    @Column(length = 4000)
    private String profileJson;

    protected DailyInterestProfile() {
    }
}
