package com.example.assistant.repo;

import com.example.assistant.model.UserActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    List<UserActivity> findTop30ByUserIdOrderByOccurredAtDesc(String userId);

    List<UserActivity> findTop100ByUserIdOrderByOccurredAtDesc(String userId);

    List<UserActivity> findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(String userId, LocalDateTime occurredAt);

    long countByUserId(String userId);

    long countByUserIdAndOccurredAtAfter(String userId, LocalDateTime occurredAt);

    @Query("select distinct a.userId from UserActivity a order by a.userId")
    List<String> findDistinctUserIds();
}
