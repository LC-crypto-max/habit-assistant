package com.example.assistant.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class HabitSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;

    private String habitName;

    private String content;

    private LocalDate recordDate;

    private String remark;

    private LocalDateTime createdAt;

    protected HabitSubmission() {
    }

    public HabitSubmission(String nickname, String habitName, String content, LocalDate recordDate, String remark,
            LocalDateTime createdAt) {
        this.nickname = nickname;
        this.habitName = habitName;
        this.content = content;
        this.recordDate = recordDate;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getHabitName() {
        return habitName;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public String getRemark() {
        return remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
