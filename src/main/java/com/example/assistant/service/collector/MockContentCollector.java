package com.example.assistant.service.collector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MockContentCollector implements PlatformCollector {

    private final List<CollectedContent> catalog = List.of(
            new CollectedContent("douyin", "mock-douyin-ai-video", "抖音 AI 工具短视频：3 分钟搭建个人效率流",
                    "https://www.douyin.com/search/AI%20效率%20工具",
                    "抖音示例源",
                    "适合用用户主动提交的抖音搜索、点赞、收藏和观看记录，识别短视频偏好并进入每日推荐。",
                    LocalDateTime.now().minusHours(4),
                    List.of("douyin", "抖音", "ai", "效率", "短视频")),
            new CollectedContent("douyin", "mock-douyin-life-food", "抖音生活方式推荐：低脂晚餐和一人食灵感",
                    "https://www.douyin.com/search/低脂晚餐%20一人食",
                    "抖音示例源",
                    "当用户频繁观看美食、健身、生活方式内容时，推荐服务可以从这些标签中生成更贴近当天状态的内容。",
                    LocalDateTime.now().minusHours(7),
                    List.of("douyin", "抖音", "美食", "低脂晚餐", "生活方式")),
            new CollectedContent("douyin", "mock-douyin-java-learning", "抖音 Java 学习切片：Spring Boot 项目实战",
                    "https://www.douyin.com/search/Spring%20Boot%20项目实战",
                    "抖音示例源",
                    "把短视频学习记录沉淀为技术兴趣标签，和 B站、YouTube 的长视频行为形成互补。",
                    LocalDateTime.now().minusDays(1),
                    List.of("douyin", "抖音", "java", "spring boot", "学习")),
            new CollectedContent("bilibili", "mock-bili-java-agent", "Java Agent 与本地 AI 助手实战",
                    "https://www.bilibili.com/search?keyword=Java%20Agent%20AI",
                    "技术区示例",
                    "围绕 Java Agent、Spring Boot 和本地 AI 助手的工程化实践，适合继续扩展个人兴趣助手。",
                    LocalDateTime.now().minusHours(3),
                    List.of("java", "ai", "spring boot", "编程")),
            new CollectedContent("baidu", "mock-baidu-spring-ai", "Spring Boot 接入大模型 API 的常见架构",
                    "https://www.baidu.com/s?wd=Spring%20Boot%20大模型%20API",
                    "搜索结果示例",
                    "整理后端服务接入 LLM、异步任务、数据库落库和接口封装的常见设计方式。",
                    LocalDateTime.now().minusHours(5),
                    List.of("spring boot", "ai", "api", "后端")),
            new CollectedContent("bilibili", "mock-bili-productivity", "如何搭建自己的信息流和知识沉淀系统",
                    "https://www.bilibili.com/search?keyword=信息流%20知识沉淀",
                    "效率工具示例",
                    "从搜索词、阅读记录、收藏反馈里建立个人画像，并自动生成每日摘要。",
                    LocalDateTime.now().minusHours(8),
                    List.of("效率", "知识管理", "推荐系统", "个人画像")),
            new CollectedContent("twitter", "mock-x-ai-agent", "AI Agent 产品形态与个人自动化趋势",
                    "https://twitter.com/search?q=AI%20Agent%20automation",
                    "X 示例源",
                    "讨论 AI Agent 如何连接搜索、内容筛选、日程提醒和个人工作流。",
                    LocalDateTime.now().minusHours(10),
                    List.of("ai", "agent", "automation", "科技")),
            new CollectedContent("baidu", "mock-baidu-wechat", "微信小程序后端接口设计入门",
                    "https://www.baidu.com/s?wd=微信小程序%20后端接口%20Spring%20Boot",
                    "搜索结果示例",
                    "介绍微信小程序登录、用户绑定、推荐列表和反馈接口的后端设计。",
                    LocalDateTime.now().minusDays(1),
                    List.of("微信小程序", "spring boot", "api", "后端")),
            new CollectedContent("feishu", "mock-feishu-bot", "飞书机器人消息卡片与日报推送设计",
                    "https://open.feishu.cn/",
                    "飞书示例源",
                    "适合把每日推荐内容推送到飞书，支持标题、摘要、链接和反馈按钮。",
                    LocalDateTime.now().minusDays(1).minusHours(2),
                    List.of("飞书", "机器人", "日报", "推送")),
            new CollectedContent("bilibili", "mock-bili-h2-jpa", "H2 + JPA 快速搭建本地原型",
                    "https://www.bilibili.com/search?keyword=H2%20JPA%20Spring%20Boot",
                    "后端示例",
                    "适合本地 MVP 使用，先快速验证数据模型和接口，再迁移到 MySQL 或 PostgreSQL。",
                    LocalDateTime.now().minusDays(2),
                    List.of("java", "jpa", "h2", "spring boot")),
            new CollectedContent("baidu", "mock-baidu-recommender", "推荐系统冷启动：从关键词画像开始",
                    "https://www.baidu.com/s?wd=推荐系统%20冷启动%20关键词画像",
                    "搜索结果示例",
                    "用搜索词、观看内容和反馈构建用户画像，是个人推荐系统最容易落地的第一步。",
                    LocalDateTime.now().minusDays(2).minusHours(4),
                    List.of("推荐系统", "个人画像", "搜索词", "算法")),
            new CollectedContent("xiaohongshu", "mock-xhs-travel", "小红书城市周末路线：咖啡店、展览和Citywalk",
                    "https://www.xiaohongshu.com/search_result?keyword=Citywalk%20咖啡%20展览",
                    "小红书示例源",
                    "适合把用户在小红书收藏、搜索和浏览过的生活方式内容沉淀为兴趣画像。",
                    LocalDateTime.now().minusHours(6),
                    List.of("小红书", "citywalk", "咖啡", "展览", "生活方式")),
            new CollectedContent("xiaohongshu", "mock-xhs-ai-life", "小红书AI效率笔记：用自动化整理每日灵感",
                    "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
                    "小红书示例源",
                    "把小红书上的效率工具、读书笔记和灵感清单整理成可持续追踪的个人内容偏好。",
                    LocalDateTime.now().minusHours(9),
                    List.of("小红书", "ai", "效率", "笔记", "知识管理")),
            new CollectedContent("xiaohongshu", "mock-xhs-food", "小红书家常菜收藏夹：低脂晚餐和一人食",
                    "https://www.xiaohongshu.com/search_result?keyword=低脂晚餐%20一人食",
                    "小红书示例源",
                    "通过用户主动提交的小红书收藏和搜索词，识别美食、健身、生活方式等细分兴趣。",
                    LocalDateTime.now().minusDays(1),
                    List.of("小红书", "美食", "低脂晚餐", "一人食", "生活")));

    @Override
    public String platform() {
        return "mock";
    }

    @Override
    public List<CollectedContent> search(String userId, List<String> interestTerms) {
        if (interestTerms == null || interestTerms.isEmpty()) {
            return catalog;
        }
        return catalog.stream()
                .filter(content -> matches(content, interestTerms))
                .toList();
    }

    private boolean matches(CollectedContent content, List<String> interestTerms) {
        String haystack = String.join(" ",
                content.title(),
                content.summary(),
                String.join(" ", content.tags())).toLowerCase(Locale.ROOT);
        return interestTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(haystack::contains);
    }
}
