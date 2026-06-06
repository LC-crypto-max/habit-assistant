package com.example.assistant.codexagent.profile;

import com.example.assistant.codexagent.dto.DailyProfileDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InterestProfileBuilder {

    public DailyProfileDTO build(String userId, LocalDate date, List<InterestEventDTO> events) {
        Map<String, Double> tagScores = new LinkedHashMap<>();
        Map<String, Double> platformScores = new LinkedHashMap<>();
        double totalWeight = 0;
        for (InterestEventDTO event : events) {
            double weight = Math.max(0.1, event.weight());
            totalWeight += weight;
            platformScores.merge(event.platform().toLowerCase(), weight, Double::sum);
            for (String tag : event.tags()) {
                tagScores.merge(tag, weight, Double::sum);
            }
        }
        double denominator = totalWeight == 0 ? 1 : totalWeight;
        List<DailyProfileDTO.TagScore> topTags = tagScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(entry -> new DailyProfileDTO.TagScore(entry.getKey(), round(entry.getValue() / denominator)))
                .toList();
        Map<String, Double> platformDistribution = new LinkedHashMap<>();
        platformScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> platformDistribution.put(entry.getKey(), round(entry.getValue() / denominator)));
        List<String> intents = topTags.stream()
                .limit(5)
                .map(tag -> "推荐" + tag.tag() + "相关内容")
                .toList();
        String summary = topTags.isEmpty()
                ? "今日暂无足够兴趣数据。"
                : "用户今日主要关注 " + String.join("、", topTags.stream().map(DailyProfileDTO.TagScore::tag).toList()) + " 内容。";
        return new DailyProfileDTO(userId, date, new ArrayList<>(topTags), platformDistribution, summary, intents);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
