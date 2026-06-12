package com.example.assistant.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ActivityType {
    SEARCH,
    APP_USAGE,
    VISIT,
    WATCH,
    LIKE,
    FAVORITE,
    DISLIKE;

    @JsonCreator
    public static ActivityType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ActivityType.valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
