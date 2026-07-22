package com.example.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.assistant.service.agent.PublicLinkNormalizer;
import org.junit.jupiter.api.Test;

class PublicLinkNormalizerTest {

    private final PublicLinkNormalizer normalizer = new PublicLinkNormalizer();

    @Test
    void extractsXiaohongshuUrlFromShareText() {
        String result = normalizer.extractAndSanitize(
                "5【AI 效率笔记】复制后打开小红书 https://xhslink.com/a/AbC123，看看吧！",
                "xiaohongshu");

        assertThat(result).isEqualTo("https://xhslink.com/a/AbC123");
    }

    @Test
    void removesSensitiveAndTrackingQueryParametersBeforePersistence() {
        String result = normalizer.extractAndSanitize(
                "https://www.xiaohongshu.com/explore/demo?xsec_token=secret&utm_source=share&foo=bar",
                "xiaohongshu");

        assertThat(result).isEqualTo("https://www.xiaohongshu.com/explore/demo?foo=bar");
        assertThat(result).doesNotContain("secret", "xsec_token", "utm_source");
    }

    @Test
    void rejectsNonXiaohongshuHostForXiaohongshuTask() {
        assertThat(normalizer.extractAndSanitize(
                "https://example.com/pretend-xiaohongshu", "xiaohongshu")).isNull();
    }
}
