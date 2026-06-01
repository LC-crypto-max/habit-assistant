package com.example.assistant.repo;

import com.example.assistant.model.HabitSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitSubmissionRepository extends JpaRepository<HabitSubmission, Long> {
}
