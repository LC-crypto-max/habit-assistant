package com.example.assistant.controller;

import com.example.assistant.dto.AgentWorkerStartRequest;
import com.example.assistant.dto.AgentWorkerStartResponse;
import com.example.assistant.service.agent.LocalAgentWorkerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/worker")
public class AgentWorkerController {

    private final LocalAgentWorkerService localAgentWorkerService;

    public AgentWorkerController(LocalAgentWorkerService localAgentWorkerService) {
        this.localAgentWorkerService = localAgentWorkerService;
    }

    @PostMapping("/start-once")
    public AgentWorkerStartResponse startOnce(@RequestBody(required = false) AgentWorkerStartRequest request,
            HttpServletRequest servletRequest) {
        return localAgentWorkerService.startOnce(request, servletRequest);
    }
}
