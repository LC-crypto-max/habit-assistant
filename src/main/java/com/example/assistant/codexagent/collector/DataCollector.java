package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import java.util.List;

public interface DataCollector {

    boolean supports(String source);

    List<InterestEventDTO> collect(AgentTaskRequest request, UserDataAuthorization authorization);
}
