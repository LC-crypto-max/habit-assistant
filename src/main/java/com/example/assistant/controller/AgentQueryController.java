package com.example.assistant.controller;

import com.example.assistant.dto.AgentQueryCreateRequest;
import com.example.assistant.dto.AgentQueryResultRequest;
import com.example.assistant.dto.AgentQueryResultResponse;
import com.example.assistant.dto.AgentQueryTaskResponse;
import com.example.assistant.service.agent.AgentQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/queries")
public class AgentQueryController {

    private final AgentQueryService agentQueryService;

    public AgentQueryController(AgentQueryService agentQueryService) {
        this.agentQueryService = agentQueryService;
    }

    @PostMapping
    public AgentQueryTaskResponse create(@Valid @RequestBody AgentQueryCreateRequest request) {
        return agentQueryService.create(request);
    }

    @GetMapping("/{taskId}")
    public AgentQueryTaskResponse find(@PathVariable String taskId) {
        return agentQueryService.find(taskId);
    }

    @PostMapping("/claim-next")
    public AgentQueryTaskResponse claimNext() {
        return agentQueryService.claimNext();
    }

    @PostMapping("/{taskId}/result")
    public AgentQueryResultResponse complete(@PathVariable String taskId,
            @Valid @RequestBody AgentQueryResultRequest request) {
        return agentQueryService.complete(taskId, request);
    }
}
