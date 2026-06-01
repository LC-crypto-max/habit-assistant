package com.example.assistant.repo;

import com.example.assistant.model.Recommendation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdAndRecommendationDateOrderByScoreDesc(String userId, LocalDate recommendationDate);

    List<Recommendation> findByUserIdAndRecommendationDateOrderByCreatedAtDesc(String userId, LocalDate recommendationDate);

    List<Recommendation> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndRecommendationDateAndContentItemId(String userId, LocalDate recommendationDate,
            Long contentItemId);

    void deleteByUserIdAndRecommendationDate(String userId, LocalDate recommendationDate);
}
