package com.example.assistant.service.behavior;

import com.example.assistant.dto.BehaviorEventMessage;

public interface BehaviorEventPublisher {

    boolean publish(BehaviorEventMessage message);
}
