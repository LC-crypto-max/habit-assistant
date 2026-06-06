package com.example.assistant.codexagent.recommender;

import com.example.assistant.codexagent.dto.DailyProfileDTO;
import com.example.assistant.codexagent.dto.RecommendationDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContentRecommender {

    public RecommendationDTO recommend(DailyProfileDTO profile, int topK, List<String> categories) {
        List<RecommendationDTO.RecommendationItem> items = new ArrayList<>();
        for (DailyProfileDTO.TagScore tag : profile.topTags()) {
            if (items.size() >= topK) {
                break;
            }
            items.add(item(profile, tag.tag(), categories));
        }
        if (items.isEmpty()) {
            items.add(new RecommendationDTO.RecommendationItem(
                    "整理今日兴趣输入",
                    "今日采集到的兴趣数据较少，建议先导入浏览器历史、App 使用概况或本地笔记。",
                    "职业成长",
                    List.of("兴趣画像", "个人知识管理"),
                    "本地笔记 / GitHub",
                    "个人兴趣画像 数据整理 方法"));
        }
        return new RecommendationDTO(profile.userId(), profile.date(), items);
    }

    private RecommendationDTO.RecommendationItem item(DailyProfileDTO profile, String tag, List<String> categories) {
        return switch (tag) {
            case "Java后端" -> new RecommendationDTO.RecommendationItem(
                    "Java 后端高频面试题深度解析",
                    "你今天多次接触 Java 后端相关内容，因此推荐继续围绕 Spring、Redis、MySQL 和 JVM 深挖。",
                    category(categories, "技术学习"),
                    List.of("Java", "Spring", "Redis", "MySQL", "JVM"),
                    "B站 / 微信公众号 / YouTube / 掘金",
                    "Java Spring Redis MySQL JVM 面试题 原理");
            case "AI工具" -> new RecommendationDTO.RecommendationItem(
                    "AI Agent 本地工作流实践",
                    "你今天的兴趣标签包含 AI 工具和 Agent，适合继续探索本地自动化和结构化回调。",
                    category(categories, "AI工具"),
                    List.of("AI", "Agent", "Codex", "自动化"),
                    "GitHub / YouTube / B站",
                    "AI Agent Codex 本地工具 工作流 实践");
            case "日语学习" -> new RecommendationDTO.RecommendationItem(
                    "日语 N1 高频语法和阅读训练",
                    "你今天出现日语学习相关兴趣，可以安排少量高频语法和阅读训练。",
                    category(categories, "日语学习"),
                    List.of("日语", "JLPT", "N1"),
                    "YouTube / B站 / 本地笔记",
                    "JLPT N1 高频语法 阅读 训练");
            case "生活方式" -> new RecommendationDTO.RecommendationItem(
                    "生活方式灵感整理",
                    "你今天的小红书或本地笔记信号偏向生活方式，适合整理成可执行清单。",
                    category(categories, "生活方式"),
                    List.of("生活方式", "效率", "旅行"),
                    "小红书公开链接 / 本地笔记",
                    "生活方式 效率 清单 灵感");
            case "娱乐内容" -> new RecommendationDTO.RecommendationItem(
                    "高质量视频内容筛选",
                    "你今天的视频平台使用较多，建议把娱乐和学习内容分开管理。",
                    category(categories, "娱乐内容"),
                    List.of("视频", "B站", "YouTube"),
                    "B站 / YouTube",
                    "高质量 视频 学习 娱乐 内容筛选");
            default -> new RecommendationDTO.RecommendationItem(
                    tag + "主题内容探索",
                    "该推荐基于你今天采集到的兴趣标签：" + tag + "。",
                    category(categories, "职业成长"),
                    List.of(tag),
                    "GitHub / 知乎 / B站 / YouTube",
                    tag + " 入门 实践 案例");
        };
    }

    private String category(List<String> categories, String preferred) {
        return categories == null || categories.isEmpty() || categories.contains(preferred)
                ? preferred
                : categories.get(0);
    }
}
