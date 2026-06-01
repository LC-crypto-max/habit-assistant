package com.example.assistant.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KeywordExtractor {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9+#.]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "this", "that", "http", "https",
            "一个", "一些", "怎么", "如何", "什么", "今天", "每天", "内容", "视频", "搜索");

    public List<String> extract(String text, List<String> tags) {
        Set<String> terms = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                addToken(terms, tag);
            }
        }
        if (text != null) {
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                addToken(terms, matcher.group());
            }
        }
        return new ArrayList<>(terms);
    }

    private void addToken(Set<String> terms, String raw) {
        if (raw == null) {
            return;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (token.length() < 2 || STOP_WORDS.contains(token)) {
            return;
        }
        terms.add(token);
    }
}
