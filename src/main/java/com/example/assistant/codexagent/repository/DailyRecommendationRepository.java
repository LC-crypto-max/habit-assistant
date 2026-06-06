package com.example.assistant.codexagent.repository;

import com.example.assistant.codexagent.entity.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, String> {
}
