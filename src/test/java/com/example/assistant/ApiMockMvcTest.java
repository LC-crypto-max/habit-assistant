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
                .andExpect(jsonPath("$.refreshHours").value(6))
                .andExpect(jsonPath("$.expired").value(false));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

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
}
