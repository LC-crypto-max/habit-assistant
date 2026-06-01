package com.example.assistant.controller;

import com.example.assistant.dto.HabitSubmissionRequest;
import com.example.assistant.dto.HabitSubmissionResponse;
import com.example.assistant.dto.SubmitResponse;
import com.example.assistant.service.HabitSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/habits")
public class HabitSubmissionController {

    private final HabitSubmissionService service;

    public HabitSubmissionController(HabitSubmissionService service) {
        this.service = service;
    }

    @PostMapping("/submit")
    public SubmitResponse<HabitSubmissionResponse> submit(@Valid @RequestBody HabitSubmissionRequest request) {
        return new SubmitResponse<>(true, "提交成功", service.submit(request));
    }
}
