package com.example.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.assistant.service.ActivityService;
import com.example.assistant.service.PlatformUsageSummaryService;
import com.example.assistant.service.RecommendationService;
import com.example.assistant.service.UserContext;
import com.example.assistant.service.UserProfileBuilder;
import com.example.assistant.service.XiaohongshuDemoSnapshotService;
import java.util.List;
import org.junit.jupiter.api.Test;

class XiaohongshuDemoSnapshotServiceTest {

    @Test
    void returnsDegradedSnapshotWhenIndependentComponentsFail() {
        ActivityService activityService = mock(ActivityService.class);
        PlatformUsageSummaryService usageService = mock(PlatformUsageSummaryService.class);
        UserProfileBuilder profileBuilder = mock(UserProfileBuilder.class);
        RecommendationService recommendationService = mock(RecommendationService.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.resolve("demo-user")).thenReturn("demo-user");
        when(usageService.xiaohongshu("demo-user")).thenThrow(new IllegalStateException("database unavailable"));
        when(activityService.recent("demo-user")).thenReturn(List.of());
        when(profileBuilder.build("demo-user")).thenThrow(new IllegalStateException("profile unavailable"));
        when(recommendationService.todaySummary("demo-user"))
                .thenThrow(new IllegalStateException("recommendations unavailable"));

        var service = new XiaohongshuDemoSnapshotService(
                activityService, usageService, profileBuilder, recommendationService, userContext);
        var response = service.snapshot("demo-user");

        assertThat(response.userId()).isEqualTo("demo-user");
        assertThat(response.degraded()).isTrue();
        assertThat(response.readiness().ready()).isFalse();
        assertThat(response.visits()).isEmpty();
        assertThat(response.components()).extracting(component -> component.status())
                .containsExactly("FAILED", "UP", "FAILED", "FAILED");
    }
}
