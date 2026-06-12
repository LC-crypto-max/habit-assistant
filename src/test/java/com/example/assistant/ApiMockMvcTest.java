package com.example.assistant;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void healthEndpointWorksWithoutStartingPort8080() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/recommendations/today")
                        .param("userId", "no-data-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("no-data-user"))
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.behaviorCount24h").value(0))
                .andExpect(jsonPath("$.recommendations", hasSize(0)))
                .andExpect(jsonPath("$.message").value("暂无推荐数据，请先创建采集任务并启动 worker。"));

        mockMvc.perform(get("/api/recommendations/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.status").value("EMPTY"));
    }

    @Test
    @Order(2)
    void habitSubmissionValidationWorks() throws Exception {
        mockMvc.perform(post("/api/habits/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "小李",
                                  "habitName": "阅读打卡",
                                  "content": "今天阅读了 Spring Boot 部署文档。",
                                  "recordDate": "2026-05-31",
                                  "remark": "通过 Nginx 页面提交"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("提交成功"))
                .andExpect(jsonPath("$.data.habitName").value("阅读打卡"));

        mockMvc.perform(post("/api/habits/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "小李",
                                  "content": "缺少习惯名称",
                                  "recordDate": "2026-05-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.habitName").exists());

        mockMvc.perform(post("/api/habits/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "小李",
                                  "habitName": "阅读打卡",
                                  "content": "%s",
                                  "recordDate": "2026-05-31"
                                }
                                """.formatted("太长".repeat(260))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.content").exists());

        mockMvc.perform(post("/api/habits/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "小李",
                                  "habitName": "阅读打卡",
                                  "content": "日期格式错误",
                                  "recordDate": "2026/05/31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST_BODY"));
    }

    @Test
    @Order(3)
    void miniProgramUploadAndDashboardWork() throws Exception {
        mockMvc.perform(post("/api/mini/users/alice/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "微信小程序推荐入口",
                                  "url": "https://example.com/wechat-mini",
                                  "platform": "wechat-mini",
                                  "tags": ["微信", "推荐", "Java"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VISIT"))
                .andExpect(jsonPath("$.title").value("微信小程序推荐入口"));

        mockMvc.perform(post("/api/mini/users/alice/search-terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "飞书机器人 推荐系统",
                                  "platform": "baidu"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SEARCH"));

        mockMvc.perform(get("/api/mini/users/alice/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.userId").value("alice"))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(4)
    void recommendationSearchGenerationRefreshPolicyAndFeishuWork() throws Exception {
        MvcResult searchResult = mockMvc.perform(post("/api/recommendations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "keyword": "AI 编程",
                                  "platform": "web-search",
                                  "refresh": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.keyword").value("AI 编程"))
                .andExpect(jsonPath("$.profile.userId").value("alice"))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        Integer recommendationId = JsonPath.read(searchResult.getResponse().getContentAsString(),
                "$.recommendations[0].id");
        mockMvc.perform(post("/api/recommendations/{id}/feedback", recommendationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedback": "LIKE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("LIKE"));

        mockMvc.perform(get("/api/recommendations/refresh-policy")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.recentActivityCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.refreshHours").value(12))
                .andExpect(jsonPath("$.expired").value(false));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/recommendations/today")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.behaviorCount24h").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.message").value("已同步最新访问记录和推荐。"));

        MvcResult todayResult = mockMvc.perform(get("/api/v1/recommendations/today")
                        .param("userId", "alice")
                        .param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.profileTags", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].score").value(greaterThanOrEqualTo(0.1)))
                .andExpect(jsonPath("$.items[0].reason").exists())
                .andExpect(jsonPath("$.items[0].matchedTags").isArray())
                .andExpect(jsonPath("$.items[0].actions.click").exists())
                .andExpect(jsonPath("$.items[0].actions.notInterested").exists())
                .andReturn();

        Integer v1RecommendationId = JsonPath.read(todayResult.getResponse().getContentAsString(),
                "$.items[0].id");
        mockMvc.perform(post("/api/v1/recommendations/{id}/click", v1RecommendationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("READ"));

        mockMvc.perform(post("/api/v1/recommendations/{id}/not-interested", v1RecommendationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("DISLIKE"));

        mockMvc.perform(post("/api/integrations/feishu/push-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "webhookUrl": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未配置飞书机器人 webhook，已跳过实际推送。"));
    }

    @Test
    @Order(5)
    void activityUploadMigrationAndHistoryTriggerWork() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "type": "WATCH",
                                  "platform": "bilibili-app",
                                  "title": "检测到正在使用哔哩哔哩",
                                  "text": "本地脚本检测到正在使用 Bilibili 客户端",
                                  "tags": ["app-usage", "bilibili"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WATCH"))
                .andExpect(jsonPath("$.platform").value("bilibili-app"));

        mockMvc.perform(post("/api/datasources/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "userId": "alice",
                                      "platform": "xiaohongshu",
                                      "type": "FAVORITE",
                                      "title": "小红书AI效率笔记收藏",
                                      "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
                                      "summary": "用户收藏的小红书效率笔记",
                                      "tags": ["小红书", "AI", "效率"]
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "bilibili",
                                      "type": "WATCH",
                                      "title": "B站 Spring Boot 推荐系统视频",
                                      "url": "https://www.bilibili.com/video/BV1demo",
                                      "summary": "观看了推荐系统实战视频",
                                      "tags": ["B站", "Java", "推荐系统"]
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "youtube",
                                      "type": "WATCH",
                                      "title": "YouTube AI Agent Tutorial",
                                      "url": "https://www.youtube.com/watch?v=demo",
                                      "summary": "AI Agent 教程字幕摘要",
                                      "tags": ["YouTube", "AI", "Agent"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.activities", hasSize(3)))
                .andExpect(jsonPath("$.activities[0].platform").value("xiaohongshu"))
                .andExpect(jsonPath("$.activities[1].platform").value("bilibili"))
                .andExpect(jsonPath("$.activities[2].platform").value("youtube"));

        mockMvc.perform(post("/api/v1/behavior-events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "userId": "alice",
                                      "platform": "xiaohongshu",
                                      "source": "browser-history",
                                      "externalId": "edge-history-101",
                                      "type": "VISIT",
                                      "title": "Xiaohongshu AI workflow note",
                                      "url": "https://www.xiaohongshu.com/explore/demo-note",
                                      "summary": "来自 Edge 浏览器历史的访问记录，visitCount=3",
                                      "tags": ["xiaohongshu", "browser-history", "ai", "workflow"],
                                      "confidence": "MEDIUM",
                                      "dataLevel": "BROWSER_HISTORY",
                                      "detectionReason": "browser_history",
                                      "matchedKeyword": "xiaohongshu.com",
                                      "rawEvidence": {
                                        "browser": "edge",
                                        "domain": "xiaohongshu.com",
                                        "visitCount": 3
                                      }
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "wechat",
                                      "source": "wechat-mini",
                                      "externalId": "wechat-link-001",
                                      "type": "VISIT",
                                      "title": "WeChat reading list",
                                      "url": "https://mp.weixin.qq.com/s/demo",
                                      "summary": "Uploaded from mini program client.",
                                      "tags": ["wechat", "reading"]
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "bilibili",
                                      "source": "agent-reach",
                                      "externalId": "BV1demo",
                                      "type": "WATCH",
                                      "title": "Bilibili Spring Boot recommendation video",
                                      "url": "https://www.bilibili.com/video/BV1demo",
                                      "summary": "Watched recommendation-system practice video.",
                                      "tags": ["bilibili", "java", "recommendation"]
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "douyin",
                                      "source": "manual-export",
                                      "externalId": "douyin-aweme-001",
                                      "type": "WATCH",
                                      "title": "Douyin AI productivity short video",
                                      "url": "https://www.douyin.com/search/AI%20productivity",
                                      "summary": "Uploaded from a compliant Douyin viewing export.",
                                      "tags": ["douyin", "ai", "productivity", "short-video"]
                                    },
                                    {
                                      "userId": "alice",
                                      "platform": "douyin",
                                      "source": "visible-window",
                                      "externalId": "douyin_widget-35524",
                                      "type": "APP_USAGE",
                                      "title": "正在使用抖音",
                                      "url": "",
                                      "summary": "本地代理检测到窗口：抖音",
                                      "text": "process=douyin_widget pid=35524 title=抖音",
                                      "occurredAt": "2026-06-06T16:21:05",
                                      "tags": ["app-usage", "visible-window", "douyin", "short-video"],
                                      "confidence": "LOW",
                                      "dataLevel": "APP_USAGE_SNAPSHOT",
                                      "detectionReason": "window_title",
                                      "matchedKeyword": "抖音",
                                      "rawEvidence": {
                                        "processName": "douyin_widget",
                                        "windowTitle": "抖音",
                                        "domain": "",
                                        "visitCount": 0
                                      }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(5))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.messagesPublished").value(0))
                .andExpect(jsonPath("$.activities", hasSize(5)))
                .andExpect(jsonPath("$.activities[0].platform").value("xiaohongshu"))
                .andExpect(jsonPath("$.activities[0].url").value("https://www.xiaohongshu.com/explore/demo-note"))
                .andExpect(jsonPath("$.activities[0].confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.activities[0].dataLevel").value("BROWSER_HISTORY"))
                .andExpect(jsonPath("$.activities[0].rawEvidence.browser").value("edge"))
                .andExpect(jsonPath("$.activities[1].platform").value("wechat"))
                .andExpect(jsonPath("$.activities[2].platform").value("bilibili"))
                .andExpect(jsonPath("$.activities[3].platform").value("douyin"))
                .andExpect(jsonPath("$.activities[4].platform").value("douyin"))
                .andExpect(jsonPath("$.activities[4].type").value("APP_USAGE"))
                .andExpect(jsonPath("$.activities[4].confidence").value("LOW"))
                .andExpect(jsonPath("$.activities[4].dataLevel").value("APP_USAGE_SNAPSHOT"))
                .andExpect(jsonPath("$.activities[4].rawEvidence.processName").value("douyin_widget"))
                .andExpect(jsonPath("$.activities[4].occurredAt").value("2026-06-06T16:21:05"));

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "type": "VISIT",
                                  "platform": "xiaohongshu",
                                  "title": "正在使用小红书",
                                  "text": "本地 Codex 代理检测到窗口：小红书",
                                  "tags": ["app-usage", "visible-window", "xiaohongshu"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "type": "VISIT",
                                  "platform": "xiaohongshu",
                                  "title": "小红书 AI 工具笔记",
                                  "url": "https://www.xiaohongshu.com/explore/test-note",
                                  "text": "浏览器历史导入的小红书访问记录",
                                  "tags": ["browser-history", "xiaohongshu"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "type": "VISIT",
                                  "platform": "xiaohongshu",
                                  "title": "小红书公开页面摘要",
                                  "url": "https://www.xiaohongshu.com/explore/page-content",
                                  "text": "页面可见内容摘要：AI 工作流、效率工具、笔记整理。",
                                  "tags": ["page-visit", "browser-extension", "xiaohongshu"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/platforms/xiaohongshu/usage-summary")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("xiaohongshu"))
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.overallConfidence").value("HIGH"))
                .andExpect(jsonPath("$.windowsAppSignals", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.browserHistorySignals", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.pageContentSignals", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.summary").value("检测到你今天访问过小红书公开页面内容。"));

        mockMvc.perform(get("/api/profile/daily")
                        .param("userId", "alice")
                        .param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.last7Days.days").value(7))
                .andExpect(jsonPath("$.last7Days.activityCount").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.last7Days.platformCounts.douyin").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.last30Days.days").value(30))
                .andExpect(jsonPath("$.last30Days.activityCount").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.topTags", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.recentActivities", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.cached").value(false));

        mockMvc.perform(get("/api/admin/migration/activities/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activities", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/admin/migration/full/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.contentCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendationCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/admin/migration/activities/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "count": 1,
                                  "activities": [
                                    {
                                      "userId": "bob",
                                      "type": "WATCH",
                                      "platform": "bilibili",
                                      "title": "MySQL 迁移测试",
                                      "url": "https://example.com/mysql",
                                      "text": "H2 数据迁移到 MySQL 的验证行为",
                                      "tags": ["MySQL", "迁移"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(post("/api/history-import/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(6)
    void agentTaskBoundaryNormalizesAndIngestsInterestItems() throws Exception {
        mockMvc.perform(post("/api/agent/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "taskId": "agent-bili-001",
                                  "adapter": "agent-reach",
                                  "intent": "read-video",
                                  "ingest": true,
                                  "items": [
                                    {
                                      "platform": "bilibili",
                                      "type": "WATCH",
                                      "externalId": "BV1demo",
                                      "title": "Bilibili recommendation architecture video",
                                      "url": "https://www.bilibili.com/video/BV1demo",
                                      "summary": "Agent Reach summarized a public recommendation architecture video.",
                                      "tags": ["bilibili", "architecture", "recommendation"]
                                    },
                                    {
                                      "platform": "xiaohongshu",
                                      "type": "FAVORITE",
                                      "externalId": "xhs-note-demo",
                                      "title": "Xiaohongshu AI workflow note",
                                      "url": "https://www.xiaohongshu.com/explore/demo",
                                      "summary": "Local agent normalized a public note into interest data.",
                                      "tags": ["xiaohongshu", "ai", "workflow"]
                                    }
                                  ],
                                  "metadata": {
                                    "privacy": "public-interest-only"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("agent-bili-001"))
                .andExpect(jsonPath("$.adapter").value("agent-reach"))
                .andExpect(jsonPath("$.status").value("INGESTED"))
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].source").value("agent-reach"))
                .andExpect(jsonPath("$.events[0].tags", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.activities", hasSize(2)))
                .andExpect(jsonPath("$.activities[0].platform").value("bilibili"))
                .andExpect(jsonPath("$.activities[1].platform").value("xiaohongshu"));
    }

    @Test
    @Order(7)
    void agentQueryQueueCanLaunchClaimAndCompleteCodexStyleTask() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/agent/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "adapter": "agent-reach",
                                  "platform": "youtube",
                                  "intent": "read-video",
                                  "url": "https://www.youtube.com/watch?v=demo",
                                  "query": "AI agent local worker"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.adapter").value("agent-reach"))
                .andExpect(jsonPath("$.platform").value("youtube"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.prompt").exists())
                .andReturn();

        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.taskId");

        mockMvc.perform(post("/api/agent/queries/claim-next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.prompt").exists());

        mockMvc.perform(post("/api/agent/queries/{taskId}/result", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "success": true,
                                  "ingest": true,
                                  "summary": "Worker returned a structured public video summary.",
                                  "items": [
                                    {
                                      "platform": "youtube",
                                      "type": "WATCH",
                                      "externalId": "demo",
                                      "title": "YouTube AI Agent Local Worker",
                                      "url": "https://www.youtube.com/watch?v=demo",
                                      "author": "Demo Channel",
                                      "summary": "The video explains how a local worker can call an agent tool and return structured JSON.",
                                      "occurredAt": "2026-06-06T16:21:05",
                                      "tags": ["youtube", "agent", "worker"]
                                    }
                                  ],
                                  "metadata": {
                                    "privacy": "public-interest-only"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.taskId").value(taskId))
                .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ingestion.status").value("INGESTED"))
                .andExpect(jsonPath("$.ingestion.imported").value(1))
                .andExpect(jsonPath("$.ingestion.activities[0].platform").value("youtube"))
                .andExpect(jsonPath("$.ingestion.activities[0].occurredAt").value("2026-06-06T16:21:05"));
    }

    @Test
    @Order(8)
    void localWorkerStartIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/agent/worker/start-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": true,
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LOCAL_WORKER_DISABLED"));
    }

    @Test
    @Order(9)
    void validationErrorShapeIsStable() throws Exception {
        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "platform": "browser"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.url").exists());

        mockMvc.perform(post("/api/recommendations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "alice",
                                  "platform": "web-search"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.keyword").exists());
    }

    @Test
    @Order(10)
    void profileV2AndRecommendationUseRealEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/behavior-events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "userId": "weak-user",
                                      "platform": "xiaohongshu",
                                      "source": "visible-window",
                                      "type": "APP_USAGE",
                                      "title": "Visible app: xiaohongshu",
                                      "summary": "Window title: 小红书",
                                      "tags": ["visible-window", "xiaohongshu"],
                                      "confidence": "LOW",
                                      "dataLevel": "APP_USAGE_SNAPSHOT",
                                      "detectionReason": "window_title"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/profile/current")
                        .param("userId", "weak-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value("v2"))
                .andExpect(jsonPath("$.topInterests", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.topInterests[0].confidence").value("LOW"));

        mockMvc.perform(post("/api/v1/behavior-events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "userId": "profile-v2-user",
                                      "platform": "browser",
                                      "source": "browser-history",
                                      "type": "VISIT",
                                      "title": "Java Spring Boot Redis 高并发缓存实践",
                                      "url": "https://example.com/java-spring-redis",
                                      "summary": "来自 Edge 浏览器历史的访问记录，visitCount=4",
                                      "tags": ["browser-history", "Java后端", "Redis", "Spring"],
                                      "confidence": "MEDIUM",
                                      "dataLevel": "BROWSER_HISTORY",
                                      "detectionReason": "browser_history",
                                      "matchedKeyword": "java",
                                      "rawEvidence": {"browser": "edge", "domain": "example.com", "visitCount": 4}
                                    },
                                    {
                                      "userId": "profile-v2-user",
                                      "platform": "xiaohongshu",
                                      "source": "browser-history",
                                      "type": "VISIT",
                                      "title": "小红书生活方式笔记",
                                      "url": "https://www.xiaohongshu.com/explore/demo",
                                      "summary": "来自 Edge 浏览器历史的访问记录，visitCount=3",
                                      "tags": ["browser-history", "xiaohongshu", "生活方式"],
                                      "confidence": "MEDIUM",
                                      "dataLevel": "BROWSER_HISTORY",
                                      "detectionReason": "browser_history",
                                      "matchedKeyword": "xiaohongshu.com",
                                      "rawEvidence": {"browser": "edge", "domain": "xiaohongshu.com", "visitCount": 3}
                                    },
                                    {
                                      "userId": "profile-v2-user",
                                      "platform": "desktop-app",
                                      "source": "visible-window",
                                      "type": "APP_USAGE",
                                      "title": "Visible app: WindowsTerminal",
                                      "summary": "Window title: PowerShell",
                                      "tags": ["visible-window"],
                                      "confidence": "LOW",
                                      "dataLevel": "APP_USAGE_SNAPSHOT"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/profile/current")
                        .param("userId", "profile-v2-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value("v2"))
                .andExpect(jsonPath("$.topInterests[*].name").value(org.hamcrest.Matchers.hasItem("Java后端")))
                .andExpect(jsonPath("$.platformPreferences[*].platform").value(org.hamcrest.Matchers.hasItem("xiaohongshu")))
                .andExpect(jsonPath("$.platformPreferences[0].bestDataLevel").value("BROWSER_HISTORY"))
                .andExpect(jsonPath("$.evidence", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(post("/api/recommendations/rebuild")
                        .param("userId", "profile-v2-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.recommendations[0].basedOn", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/recommendations/today")
                        .param("userId", "no-data-v2-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.recommendations", hasSize(0)));
    }
}
