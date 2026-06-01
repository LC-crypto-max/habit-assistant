package com.example.assistant.service;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.ImportResultResponse;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HistoryImportJob {

    private static final DateTimeFormatter ARCHIVE_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AssistantProperties properties;
    private final VisitRecordService visitRecordService;

    public HistoryImportJob(AssistantProperties properties, VisitRecordService visitRecordService) {
        this.properties = properties;
        this.visitRecordService = visitRecordService;
    }

    @Scheduled(cron = "${assistant.history-import.cron}")
    public void importHistoryFiles() {
        if (!properties.getHistoryImport().isEnabled()) {
            return;
        }
        importOnce();
    }

    public List<ImportResultResponse> importOnce() {
        AssistantProperties.HistoryImport config = properties.getHistoryImport();
        Path inputDir = Path.of(config.getDirectory());
        Path archiveDir = Path.of(config.getArchiveDirectory());
        Path failedDir = Path.of(config.getFailedDirectory());
        List<ImportResultResponse> results = new ArrayList<>();

        try {
            Files.createDirectories(inputDir);
            Files.createDirectories(archiveDir);
            Files.createDirectories(failedDir);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + config.getFilePattern());
            try (var stream = Files.list(inputDir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(path.getFileName()))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    results.add(importOne(file, archiveDir, failedDir));
                }
            }
            return results;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import browser history files", e);
        }
    }

    private ImportResultResponse importOne(Path file, Path archiveDir, Path failedDir) throws IOException {
        try {
            ImportResultResponse result = visitRecordService.importCsv(file);
            moveIfPossible(file, archiveDir);
            return result;
        } catch (IOException | RuntimeException e) {
            moveIfPossible(file, failedDir);
            throw e;
        }
    }

    private boolean moveIfPossible(Path file, Path targetDir) {
        try {
            move(file, targetDir);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void move(Path file, Path targetDir) throws IOException {
        String suffix = ARCHIVE_SUFFIX.format(LocalDateTime.now());
        String fileName = file.getFileName().toString();
        Path target = targetDir.resolve(suffix + "-" + fileName);
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
