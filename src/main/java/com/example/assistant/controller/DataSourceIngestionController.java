package com.example.assistant.controller;

import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.DataSourceBatchRequest;
import com.example.assistant.dto.DataSourceBatchResponse;
import com.example.assistant.dto.DataSourceEventRequest;
import com.example.assistant.service.DataSourceIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceIngestionController {

    private final DataSourceIngestionService ingestionService;

    public DataSourceIngestionController(DataSourceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/events")
    public ActivityResponse recordEvent(@Valid @RequestBody DataSourceEventRequest request) {
        return ingestionService.record(request);
    }

    @PostMapping("/events/batch")
    public DataSourceBatchResponse recordBatch(@Valid @RequestBody DataSourceBatchRequest request) {
        return ingestionService.recordBatch(request);
    }

    @PostMapping("/{platform}/events")
    public ActivityResponse recordPlatformEvent(@PathVariable String platform,
            @Valid @RequestBody DataSourceEventRequest request) {
        DataSourceEventRequest normalized = new DataSourceEventRequest(
                request.userId(),
                platform,
                request.type(),
                request.externalId(),
                request.title(),
                request.url(),
                request.author(),
                request.summary(),
                request.text(),
                request.occurredAt(),
                request.tags());
        return ingestionService.record(normalized);
    }
}
