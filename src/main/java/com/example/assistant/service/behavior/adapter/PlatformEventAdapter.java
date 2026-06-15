package com.example.assistant.service.behavior.adapter;

import com.example.assistant.dto.BehaviorEventRequest;

public interface PlatformEventAdapter {

    boolean supports(String platform);

    UnifiedBehaviorEvent normalize(BehaviorEventRequest event);
}
