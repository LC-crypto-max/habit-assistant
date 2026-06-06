package com.example.assistant.codexagent.controller;

import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.AgentTaskResponse;
import com.example.assistant.codexagent.dto.ClientAppUsageRequest;
import com.example.assistant.codexagent.service.CodexDataAgentService;
import com.example.assistant.codexagent.service.DailyInterestTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/codex-agent")
public class CodexDataAgentController {

    private final DailyInterestTaskService dailyInterestTaskService;
    private final CodexDataAgentService codexDataAgentService;

    public CodexDataAgentController(DailyInterestTaskService dailyInterestTaskService,
            CodexDataAgentService codexDataAgentService) {
        this.dailyInterestTaskService = dailyInterestTaskService;
        this.codexDataAgentService = codexDataAgentService;
    }

    @PostMapping("/daily")
    public AgentTaskResponse daily(@Valid @RequestBody AgentTaskRequest request) {
        return dailyInterestTaskService.run(request);
    }

    @PostMapping("/client/app-usage")
    public AgentTaskResponse uploadAppUsage(@Valid @RequestBody ClientAppUsageRequest request) {
        return codexDataAgentService.uploadAppUsage(request);
    }
}
