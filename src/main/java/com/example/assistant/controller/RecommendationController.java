package com.example.assistant.controller;

import com.example.assistant.dto.FeedbackRequest;
import com.example.assistant.dto.RecommendationRefreshPolicyResponse;
import com.example.assistant.dto.RecommendationResponse;
import com.example.assistant.dto.RecommendationSearchRequest;
import com.example.assistant.dto.RecommendationSearchResponse;
import com.example.assistant.dto.RecommendationTodaySummaryResponse;
import com.example.assistant.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

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

    @PostMapping("/rebuild")
    public RecommendationTodaySummaryResponse rebuild(@RequestParam(required = false) String userId) {
        recommendationService.refreshToday(userId);
        return recommendationService.todaySummary(userId);
    }

    @GetMapping("/today")
    public RecommendationTodaySummaryResponse today(@RequestParam(required = false) String userId) {
        try {
            return recommendationService.todaySummary(userId);
        } catch (RuntimeException ex) {
            log.error("Failed to load today's recommendations for userId={}", userId, ex);
            return recommendationService.emptyTodaySummary(userId, "推荐接口暂时不可用，请检查后端日志。");
        }
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
