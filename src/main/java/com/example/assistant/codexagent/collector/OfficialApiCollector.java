package com.example.assistant.codexagent.collector;

import com.example.assistant.codexagent.dto.AgentTaskRequest;
import com.example.assistant.codexagent.dto.InterestEventDTO;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataAccessScope;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OfficialApiCollector implements DataCollector {

    @Override
    public boolean supports(String source) {
        return DataAccessScope.OFFICIAL_API.name().equals(source);
    }

    @Override
    public List<InterestEventDTO> collect(AgentTaskRequest request, UserDataAuthorization authorization) {
        return List.of();
    }
}
