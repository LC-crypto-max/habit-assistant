package com.example.assistant.codexagent.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonFileStore {

    private final ObjectMapper objectMapper;

    public JsonFileStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    public <T> List<T> readList(Path path, TypeReference<List<T>> type) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON file: " + path, e);
        }
    }

    public <T> T read(Path path, Class<T> type) {
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON file: " + path, e);
        }
    }

    public void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write JSON file: " + path, e);
        }
    }

    public void appendJsonLine(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            String line = objectMapper.writeValueAsString(value) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append JSONL file: " + path, e);
        }
    }
}
