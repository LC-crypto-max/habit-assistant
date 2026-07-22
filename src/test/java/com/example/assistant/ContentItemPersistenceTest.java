package com.example.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.assistant.model.ContentItem;
import com.example.assistant.repo.ContentItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ContentItemPersistenceTest {

    @Autowired
    private ContentItemRepository repository;

    @Test
    void persistsAiSummaryLongerThanLegacy255CharacterLimit() {
        String summary = "公开笔记语义摘要".repeat(80);
        ContentItem saved = repository.saveAndFlush(new ContentItem(
                "xiaohongshu",
                "demo-long-summary",
                "小红书公开笔记",
                "https://www.xiaohongshu.com/explore/demo-long-summary",
                "公开作者",
                summary,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "a".repeat(64),
                List.of("AI", "效率")));

        assertThat(repository.findById(saved.getId()).orElseThrow().getSummary()).isEqualTo(summary);
        assertThat(summary.length()).isGreaterThan(255);
    }
}
