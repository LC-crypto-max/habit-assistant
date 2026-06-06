package com.example.assistant.codexagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class InterestEvent {

    @Id
    private String eventId;
    private String userId;
    private String source;
    private String platform;
    private String eventType;
    @Column(length = 512)
    private String title;
    @Column(length = 1024)
    private String url;
    private String author;
    @ElementCollection
    private List<String> tags = new ArrayList<>();
    @Column(length = 1000)
    private String summary;
    private OffsetDateTime timestamp;
    private double weight;

    protected InterestEvent() {
    }
}
