package com.example.assistant.service;

import com.example.assistant.dto.HabitSubmissionRequest;
import com.example.assistant.dto.HabitSubmissionResponse;
import com.example.assistant.model.HabitSubmission;
import com.example.assistant.repo.HabitSubmissionRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitSubmissionService {

    private final HabitSubmissionRepository repository;

    public HabitSubmissionService(HabitSubmissionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public HabitSubmissionResponse submit(HabitSubmissionRequest request) {
        HabitSubmission saved = repository.save(new HabitSubmission(
                request.nickname().trim(),
                request.habitName().trim(),
                request.content().trim(),
                request.recordDate(),
                request.remark() == null ? null : request.remark().trim(),
                LocalDateTime.now()));
        return toResponse(saved);
    }

    private HabitSubmissionResponse toResponse(HabitSubmission submission) {
        return new HabitSubmissionResponse(
                submission.getId(),
                submission.getNickname(),
                submission.getHabitName(),
                submission.getContent(),
                submission.getRecordDate(),
                submission.getRemark(),
                submission.getCreatedAt());
    }
}
