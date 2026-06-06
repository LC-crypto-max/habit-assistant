package com.example.assistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class AgentQueryTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, unique = true)
    private String taskId;

    @Column(length = 80)
    private String userId;

    @Column(length = 80)
    private String adapter;

    @Column(length = 80)
    private String platform;

    @Column(length = 80)
    private String intent;

    @Column(length = 1024)
    private String url;

    @Column(length = 512)
    private String query;

    @Column(length = 3000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AgentQueryStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime claimedAt;

    private LocalDateTime finishedAt;

    @Column(length = 1000)
    private String errorMessage;

    protected AgentQueryTask() {
    }

    public AgentQueryTask(String taskId, String userId, String adapter, String platform, String intent, String url,
            String query, String prompt) {
        this.taskId = taskId;
        this.userId = userId;
        this.adapter = adapter;
        this.platform = platform;
        this.intent = intent;
        this.url = url;
        this.query = query;
        this.prompt = prompt;
        this.status = AgentQueryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAdapter() {
        return adapter;
    }

    public String getPlatform() {
        return platform;
    }

    public String getIntent() {
        return intent;
    }

    public String getUrl() {
        return url;
    }

    public String getQuery() {
        return query;
    }

    public String getPrompt() {
        return prompt;
    }

    public AgentQueryStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markRunning() {
        this.status = AgentQueryStatus.RUNNING;
        this.claimedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = AgentQueryStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = AgentQueryStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
