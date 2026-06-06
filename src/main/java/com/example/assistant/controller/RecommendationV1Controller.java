package com.example.assistant.controller;

import com.example.assistant.dto.FeedbackRequest;
import com.example.assistant.dto.RecommendationResponse;
import com.example.assistant.dto.RecommendationTodayResponse;
import com.example.assistant.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationV1Controller {

    private final RecommendationService recommendationService;

    public RecommendationV1Controller(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/today")
    public RecommendationTodayResponse today(@RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return recommendationService.todayView(userId, refresh);
    }

    @PostMapping("/{id}/click")
    public RecommendationResponse click(@PathVariable Long id) {
        return recommendationService.recordClick(id);
    }

    @PostMapping("/{id}/not-interested")
    public RecommendationResponse notInterested(@PathVariable Long id) {
        return recommendationService.markNotInterested(id);
    }

    @PostMapping("/{id}/feedback")
    public RecommendationResponse feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return recommendationService.updateFeedback(id, request.feedback());
    }
}
