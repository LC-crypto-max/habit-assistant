package com.example.assistant.service;

import com.example.assistant.dto.ActivityRequest;
import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.ImportResultResponse;
import com.example.assistant.dto.VisitRecordRequest;
import com.example.assistant.model.ActivityType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VisitRecordService {

    private final ActivityService activityService;
    private final UserContext userContext;

    public VisitRecordService(ActivityService activityService, UserContext userContext) {
        this.activityService = activityService;
        this.userContext = userContext;
    }

    public ActivityResponse recordVisit(VisitRecordRequest request) {
        return activityService.record(toActivityRequest(request));
    }

    public ImportResultResponse importCsv(MultipartFile file) throws IOException {
        return importCsv(file.getInputStream(), null);
    }

    public ImportResultResponse importCsv(MultipartFile file, String userId) throws IOException {
        return importCsv(file.getInputStream(), userId);
    }

    public ImportResultResponse importCsv(Path path) throws IOException {
        return importCsv(path, null);
    }

    public ImportResultResponse importCsv(Path path, String userId) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return importCsv(inputStream, userId);
        }
    }

    public ImportResultResponse importCsv(InputStream inputStream) throws IOException {
        return importCsv(inputStream, null);
    }

    public ImportResultResponse importCsv(InputStream inputStream, String userId) throws IOException {
        List<ActivityResponse> imported = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            HeaderIndex header = HeaderIndex.defaultIndex();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    skipped++;
                    continue;
                }
                List<String> cells = parseCsvLine(line);
                if (firstLine && looksLikeHeader(cells)) {
                    header = HeaderIndex.from(cells);
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                VisitRecordRequest request = toVisitRecord(cells, header, userId);
                if (request == null) {
                    skipped++;
                    continue;
                }
                imported.add(recordVisit(request));
            }
        }

        return new ImportResultResponse(imported.size(), skipped, imported);
    }

    private ActivityRequest toActivityRequest(VisitRecordRequest request) {
        String platform = normalizePlatform(request.platform(), request.url());
        String userId = userContext.resolve(request.userId());
        List<String> tags = request.tags() == null ? List.of(platform) : request.tags();
        String text = request.title() + " " + request.url() + " " + String.join(" ", tags);
        return new ActivityRequest(
                userId,
                ActivityType.VISIT,
                platform,
                request.title(),
                request.url(),
                text,
                request.visitedAt(),
                tags,
                "MEDIUM",
                "BROWSER_HISTORY",
                "browser-history",
                "browser_history",
                request.url());
    }

    private VisitRecordRequest toVisitRecord(List<String> cells, HeaderIndex header, String importUserId) {
        String title = valueAt(cells, header.titleIndex());
        String url = valueAt(cells, header.urlIndex());
        if (title.isBlank() || url.isBlank() || !url.startsWith("http")) {
            return null;
        }
        String platform = valueAt(cells, header.platformIndex());
        LocalDateTime visitedAt = parseTime(valueAt(cells, header.timeIndex()));
        List<String> tags = splitTags(valueAt(cells, header.tagsIndex()));
        return new VisitRecordRequest(userContext.resolve(importUserId), title, url, platform, visitedAt, tags);
    }

    private String normalizePlatform(String platform, String url) {
        if (platform != null && !platform.isBlank()) {
            return platform.trim().toLowerCase(Locale.ROOT);
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "web";
            }
            return host.replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "web";
        }
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，;；|]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean looksLikeHeader(List<String> cells) {
        String joined = String.join(",", cells).toLowerCase(Locale.ROOT);
        return joined.contains("url")
                || joined.contains("title")
                || joined.contains("标题")
                || joined.contains("链接")
                || joined.contains("网址");
    }

    private String valueAt(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private record HeaderIndex(int titleIndex, int urlIndex, int platformIndex, int timeIndex, int tagsIndex) {
        static HeaderIndex defaultIndex() {
            return new HeaderIndex(0, 1, 2, 3, 4);
        }

        static HeaderIndex from(List<String> headers) {
            return new HeaderIndex(
                    find(headers, "title", "标题", "名称", "name"),
                    find(headers, "url", "链接", "网址", "地址"),
                    find(headers, "platform", "source", "平台", "来源"),
                    find(headers, "time", "visited", "last visit", "访问时间", "时间"),
                    find(headers, "tags", "tag", "标签"));
        }

        private static int find(List<String> headers, String... names) {
            for (int i = 0; i < headers.size(); i++) {
                String normalized = headers.get(i).trim().toLowerCase(Locale.ROOT);
                for (String name : names) {
                    if (normalized.contains(name)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }
}
