package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.FeishuPushResponse;
import com.example.assistant.dto.RecommendationResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeishuBotService {

    private final AssistantProperties properties;
    private final RecommendationService recommendationService;
    private final UserContext userContext;
    private final RestClient restClient;

    public FeishuBotService(AssistantProperties properties, RecommendationService recommendationService,
            UserContext userContext) {
        this.properties = properties;
        this.recommendationService = recommendationService;
        this.userContext = userContext;
        this.restClient = RestClient.builder().build();
    }

    public FeishuPushResponse pushRecommendations(String userId, String webhookUrl) {
        String resolvedUserId = userContext.resolve(userId);
        String resolvedWebhookUrl = resolveWebhook(webhookUrl);
        if (resolvedWebhookUrl == null || resolvedWebhookUrl.isBlank()) {
            return new FeishuPushResponse(false, "未配置飞书机器人 webhook，已跳过实际推送。");
        }

        List<RecommendationResponse> recommendations = recommendationService.today(resolvedUserId);
        if (recommendations.isEmpty()) {
            recommendations = recommendationService.generateToday(resolvedUserId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", Map.of("text", buildText(resolvedUserId, recommendations)));
        restClient.post().uri(resolvedWebhookUrl).body(body).retrieve().toBodilessEntity();
        return new FeishuPushResponse(true, "已推送 " + recommendations.size() + " 条推荐到飞书");
    }

    private String resolveWebhook(String webhookUrl) {
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            return webhookUrl.trim();
        }
        return properties.getFeishu().getWebhookUrl();
    }

    private String buildText(String userId, List<RecommendationResponse> recommendations) {
        StringBuilder builder = new StringBuilder();
        builder.append("个人兴趣助手 - 今日推荐\n");
        builder.append("用户：").append(userId).append("\n\n");
        int index = 1;
        for (RecommendationResponse recommendation : recommendations.stream().limit(5).toList()) {
            builder.append(index++).append(". ")
                    .append(recommendation.content().title()).append("\n")
                    .append(recommendation.reason()).append("\n")
                    .append(recommendation.content().url()).append("\n\n");
        }
        return builder.toString();
    }
}
