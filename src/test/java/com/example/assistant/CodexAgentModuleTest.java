package com.example.assistant;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.assistant.codexagent.collector.AppUsageCollector;
import com.example.assistant.codexagent.collector.BrowserHistoryCollector;
import com.example.assistant.codexagent.collector.LocalFileCollector;
import com.example.assistant.codexagent.dto.DailyProfileDTO;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.dto.RecommendationDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.policy.PolicyCheckResult;
import com.example.assistant.codexagent.profile.InterestProfileBuilder;
import com.example.assistant.codexagent.recommender.ContentRecommender;
import com.example.assistant.codexagent.sanitizer.PrivacySanitizer;
import com.example.assistant.codexagent.service.UserDataAuthorizationService;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class CodexAgentModuleTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataPolicyChecker policyChecker;
    @Autowired
    private PrivacySanitizer sanitizer;
    @Autowired
    private UserDataAuthorizationService authorizationService;
    @Autowired
    private AppUsageCollector appUsageCollector;
    @Autowired
    private BrowserHistoryCollector browserHistoryCollector;
    @Autowired
    private LocalFileCollector localFileCollector;
    @Autowired
    private InterestProfileBuilder profileBuilder;
    @Autowired
    private ContentRecommender recommender;

    @BeforeEach
    void cleanStore() throws Exception {
        delete(Path.of("target/codex-agent-test"));
    }

    @Test
    void userAuthorizationGrantSucceeds() throws Exception {
        grant("local_user", "2026-12-31T23:59:59+09:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.authorizationId").exists());
    }

    @Test
    void userAuthorizationRevokeSucceeds() throws Exception {
        MvcResult result = grant("local_user", "2026-12-31T23:59:59+09:00").andReturn();
        String authorizationId = JsonPath.read(result.getResponse().getContentAsString(), "$.authorizationId");
        mockMvc.perform(post("/api/user-data-authorization/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"local_user","authorizationId":"%s"}
                                """.formatted(authorizationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void unauthorizedDailyTaskIsRejected() throws Exception {
        daily("local_user", "APP_USAGE_SUMMARY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void expiredAuthorizationIsRejected() throws Exception {
        grant("local_user", "2020-01-01T00:00:00+09:00");
        daily("local_user", "APP_USAGE_SUMMARY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void unauthorizedSourceIsRejected() throws Exception {
        mockMvc.perform(post("/api/user-data-authorization/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseGrant("local_user", "2026-12-31T23:59:59+09:00")
                                .replace("\"PUBLIC_URL_METADATA\",", "")))
                .andExpect(status().isOk());
        daily("local_user", "PUBLIC_URL_METADATA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void dataPolicyRejectsCookie() {
        PolicyCheckResult result = policyChecker.checkText("cookie=secret");
        assertFalse(result.allowed());
    }

    @Test
    void dataPolicyRejectsToken() {
        assertFalse(policyChecker.checkText("token=abcdef").allowed());
    }

    @Test
    void dataPolicyRejectsWechatChatHistory() {
        assertFalse(policyChecker.checkText("读取微信聊天记录").allowed());
    }

    @Test
    void dataPolicyRejectsPrivateMessage() {
        assertFalse(policyChecker.checkText("同步小红书私信").allowed());
    }

    @Test
    void dataPolicyRejectsPathOutsideAllowedPaths() {
        assertFalse(policyChecker.checkPath("C:/Windows/System32/config", List.of("data/local-notes")).allowed());
    }

    @Test
    void privacySanitizerMasksPhone() {
        assertTrue(sanitizer.sanitize("13812345678").contains("138****5678"));
    }

    @Test
    void privacySanitizerMasksEmail() {
        assertTrue(sanitizer.sanitize("abc@example.com").contains("a***@example.com"));
    }

    @Test
    void appUsageCollectorReadsSample() throws Exception {
        grant("local_user", "2026-12-31T23:59:59+09:00");
        UserDataAuthorization auth = authorizationService.currentAuthorization("local_user");
        assertTrue(appUsageCollector.collect(agentRequest("local_user", "APP_USAGE_SUMMARY"), auth).size() > 0);
    }

    @Test
    void browserHistoryCollectorReadsSample() throws Exception {
        grant("local_user", "2026-12-31T23:59:59+09:00");
        UserDataAuthorization auth = authorizationService.currentAuthorization("local_user");
        assertTrue(browserHistoryCollector.collect(agentRequest("local_user", "BROWSER_HISTORY"), auth).size() > 0);
    }

    @Test
    void localNotesCollectorReadsMarkdown() throws Exception {
        grant("local_user", "2026-12-31T23:59:59+09:00");
        UserDataAuthorization auth = authorizationService.currentAuthorization("local_user");
        assertTrue(localFileCollector.collect(agentRequest("local_user", "LOCAL_NOTES"), auth).size() > 0);
    }

    @Test
    void interestProfileBuilderCreatesTopTags() {
        DailyProfileDTO profile = profileBuilder.build("local_user", LocalDate.parse("2026-06-06"),
                List.of(new InterestEventDTO("e1", "local_user", "BROWSER_HISTORY", "GITHUB", "VISIT",
                        "Java Redis Agent", "https://github.com/demo", "", List.of("Java后端", "AI工具"),
                        "Java Redis Agent", OffsetDateTime.now(), 2.0, Map.of())));
        assertFalse(profile.topTags().isEmpty());
    }

    @Test
    void contentRecommenderCreatesQuery() {
        DailyProfileDTO profile = new DailyProfileDTO("local_user", LocalDate.parse("2026-06-06"),
                List.of(new DailyProfileDTO.TagScore("Java后端", 0.9)), Map.of("github", 1.0),
                "用户关注 Java 后端。", List.of("推荐 Java 后端内容"));
        RecommendationDTO recommendation = recommender.recommend(profile, 10, List.of("技术学习"));
        assertFalse(recommendation.recommendations().get(0).query().isBlank());
    }

    @Test
    void codexDataAgentServiceCompletesDailyTask() throws Exception {
        grant("local_user", "2026-12-31T23:59:59+09:00");
        mockMvc.perform(post("/api/codex-agent/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": "daily_2026_06_06",
                                  "userId": "local_user",
                                  "action": "DAILY_INTEREST_COLLECT_AND_RECOMMEND",
                                  "date": "2026-06-06",
                                  "sources": ["APP_USAGE_SUMMARY", "BROWSER_HISTORY", "LOCAL_NOTES"],
                                  "maxRecords": 300,
                                  "recommendation": {
                                    "topK": 10,
                                    "language": "zh-CN",
                                    "categories": ["技术学习", "生活方式", "娱乐内容", "职业成长", "AI工具", "日语学习"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.eventsCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.profilePath").exists())
                .andExpect(jsonPath("$.recommendationsPath").exists())
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions grant(String userId, String expireAt) throws Exception {
        return mockMvc.perform(post("/api/user-data-authorization/grant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(baseGrant(userId, expireAt)));
    }

    private String baseGrant(String userId, String expireAt) {
        return """
                {
                  "userId": "%s",
                  "grantedScopes": ["APP_USAGE_SUMMARY", "BROWSER_HISTORY", "PUBLIC_URL_METADATA", "LOCAL_NOTES"],
                  "allowedApps": ["com.xingin.xhs", "com.tencent.mm", "com.ss.android.ugc.aweme", "com.google.android.youtube", "tv.danmaku.bili"],
                  "allowedDomains": ["xiaohongshu.com", "weixin.qq.com", "douyin.com", "youtube.com", "bilibili.com", "zhihu.com", "github.com", "csdn.net", "juejin.cn"],
                  "allowedPaths": ["data/raw", "data/imports", "data/local-notes", "src/test/resources/fixtures/codex-agent/local-notes"],
                  "privacy": {"sanitize": true, "keepRawText": false, "allowSensitiveData": false},
                  "expireAt": "%s"
                }
                """.formatted(userId, expireAt);
    }

    private org.springframework.test.web.servlet.ResultActions daily(String userId, String source) throws Exception {
        return mockMvc.perform(post("/api/codex-agent/daily")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "taskId": "daily_test",
                          "userId": "%s",
                          "action": "DAILY_INTEREST_COLLECT_AND_RECOMMEND",
                          "date": "2026-06-06",
                          "sources": ["%s"],
                          "maxRecords": 10,
                          "recommendation": {"topK": 3, "language": "zh-CN", "categories": ["技术学习"]}
                        }
                        """.formatted(userId, source)));
    }

    private com.example.assistant.codexagent.dto.AgentTaskRequest agentRequest(String userId, String source) {
        return new com.example.assistant.codexagent.dto.AgentTaskRequest("task", userId,
                "DAILY_INTEREST_COLLECT_AND_RECOMMEND", LocalDate.parse("2026-06-06"),
                List.of(com.example.assistant.codexagent.policy.DataAccessScope.valueOf(source)), 100,
                new com.example.assistant.codexagent.dto.AgentTaskRequest.RecommendationOptions(10, "zh-CN",
                        List.of("技术学习")));
    }

    private void delete(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
