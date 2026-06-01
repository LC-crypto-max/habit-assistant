package com.example.assistant.controller;

import com.example.assistant.dto.FeedbackRequest;
import com.example.assistant.dto.RecommendationRefreshPolicyResponse;
import com.example.assistant.dto.RecommendationResponse;
import com.example.assistant.dto.RecommendationSearchRequest;
import com.example.assistant.dto.RecommendationSearchResponse;
import com.example.assistant.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/generate")
    public List<RecommendationResponse> generateToday(@RequestParam(required = false) String userId) {
        return recommendationService.generateToday(userId);
    }

    @PostMapping("/refresh")
    public List<RecommendationResponse> refreshToday(@RequestParam(required = false) String userId) {
        return recommendationService.refreshToday(userId);
    }

    @GetMapping("/today")
    public List<RecommendationResponse> today(@RequestParam(required = false) String userId) {
        return recommendationService.autoRefreshToday(userId);
    }

    @GetMapping("/refresh-policy")
    public RecommendationRefreshPolicyResponse refreshPolicy(@RequestParam(required = false) String userId) {
        return recommendationService.refreshPolicy(userId);
    }

    @PostMapping("/search")
    public RecommendationSearchResponse searchAndRecommend(@Valid @RequestBody RecommendationSearchRequest request) {
        return recommendationService.searchAndRecommend(
                request.userId(),
                request.keyword(),
                request.platform(),
                request.refresh());
    }

    @PostMapping("/{id}/feedback")
    public RecommendationResponse feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return recommendationService.updateFeedback(id, request.feedback());
    }
}
