package com.example.assistant.controller;

import com.example.assistant.dto.BehaviorEventBatchRequest;
import com.example.assistant.dto.BehaviorEventBatchResponse;
import com.example.assistant.service.behavior.BehaviorEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/behavior-events")
public class BehaviorEventController {

    private final BehaviorEventService behaviorEventService;

    public BehaviorEventController(BehaviorEventService behaviorEventService) {
        this.behaviorEventService = behaviorEventService;
    }

    @PostMapping("/batch")
    public BehaviorEventBatchResponse recordBatch(@Valid @RequestBody BehaviorEventBatchRequest request) {
        return behaviorEventService.recordBatch(request);
    }
}
