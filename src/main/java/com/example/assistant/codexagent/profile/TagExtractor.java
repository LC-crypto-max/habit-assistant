package com.example.assistant.codexagent.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TagExtractor {

    public List<String> extract(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> tags = new LinkedHashSet<>();
        addIf(tags, lower, "Java后端", "java", "spring", "redis", "mysql", "mq", "jvm", "分布式");
        addIf(tags, lower, "AI工具", "ai", "chatgpt", "codex", "agent", "llm", "openai");
        addIf(tags, lower, "日语学习", "日语", "jlpt", "n1", "n2", "日本語");
        addIf(tags, lower, "技术学习", "github", "csdn", "juejin", "掘金", "知乎", "架构", "编程", "后端");
        addIf(tags, lower, "生活方式", "xiaohongshu", "小红书", "生活方式", "穿搭", "饮食", "旅行");
        addIf(tags, lower, "娱乐内容", "douyin", "抖音", "短视频", "娱乐", "bilibili", "youtube");
        if (tags.isEmpty() && !lower.isBlank()) {
            tags.add("综合兴趣");
        }
        return new ArrayList<>(tags);
    }

    private void addIf(Set<String> tags, String text, String tag, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                tags.add(tag);
                return;
            }
        }
    }
}
