package com.example.assistant.service.profile;

import com.example.assistant.dto.DailyProfileResponse;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assistant.profile.cache.redis", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class NoopProfileCache implements ProfileCache {

    @Override
    public Optional<DailyProfileResponse> get(String userId, LocalDate profileDate) {
        return Optional.empty();
    }

    @Override
    public void put(String userId, LocalDate profileDate, DailyProfileResponse profile) {
        // Local development does not require Redis.
    }
}
