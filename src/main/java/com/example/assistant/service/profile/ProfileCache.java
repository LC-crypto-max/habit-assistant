package com.example.assistant.service.profile;

import com.example.assistant.dto.DailyProfileResponse;
import java.time.LocalDate;
import java.util.Optional;

public interface ProfileCache {

    Optional<DailyProfileResponse> get(String userId, LocalDate profileDate);

    void put(String userId, LocalDate profileDate, DailyProfileResponse profile);
}
