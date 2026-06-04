package com.example.assistant.service.profile;

import com.example.assistant.dto.DailyProfileResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assistant.profile.cache.redis", name = "enabled", havingValue = "true")
public class RedisProfileCache implements ProfileCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisProfileCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            @Value("${assistant.profile.cache.redis.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public Optional<DailyProfileResponse> get(String userId, LocalDate profileDate) {
        String json = redisTemplate.opsForValue().get(key(userId, profileDate));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, DailyProfileResponse.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String userId, LocalDate profileDate, DailyProfileResponse profile) {
        try {
            redisTemplate.opsForValue().set(key(userId, profileDate), objectMapper.writeValueAsString(profile), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize daily profile cache", ex);
        }
    }

    private String key(String userId, LocalDate profileDate) {
        return "profile:daily:" + userId + ":" + profileDate;
    }
}
