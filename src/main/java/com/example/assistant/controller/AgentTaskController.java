package com.example.assistant.controller;

import com.example.assistant.dto.AgentTaskRequest;
import com.example.assistant.dto.AgentTaskResponse;
import com.example.assistant.service.agent.AgentTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    public AgentTaskController(AgentTaskService agentTaskService) {
        this.agentTaskService = agentTaskService;
    }

    @PostMapping
    public AgentTaskResponse process(@Valid @RequestBody AgentTaskRequest request) {
        return agentTaskService.process(request);
    }
}
