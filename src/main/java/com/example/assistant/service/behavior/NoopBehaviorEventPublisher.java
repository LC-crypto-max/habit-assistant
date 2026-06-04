package com.example.assistant.service.behavior;

import com.example.assistant.dto.BehaviorEventMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assistant.behavior.rabbitmq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class NoopBehaviorEventPublisher implements BehaviorEventPublisher {

    @Override
    public boolean publish(BehaviorEventMessage message) {
        return false;
    }
}
