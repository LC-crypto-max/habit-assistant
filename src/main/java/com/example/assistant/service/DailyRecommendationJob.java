package com.example.assistant.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyRecommendationJob {

    private final RecommendationService recommendationService;

    public DailyRecommendationJob(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Scheduled(cron = "${assistant.daily-cron}")
    public void generateDailyRecommendations() {
        recommendationService.generateToday();
    }
}
