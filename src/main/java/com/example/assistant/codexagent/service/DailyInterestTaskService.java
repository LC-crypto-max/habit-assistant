package com.example.assistant.codexagent.service;

import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.AgentTaskResponse;
import org.springframework.stereotype.Service;

@Service
public class DailyInterestTaskService {

    private final CodexDataAgentService codexDataAgentService;

    public DailyInterestTaskService(CodexDataAgentService codexDataAgentService) {
        this.codexDataAgentService = codexDataAgentService;
    }

    public AgentTaskResponse run(AgentTaskRequest request) {
        return codexDataAgentService.runDaily(request);
    }
}
