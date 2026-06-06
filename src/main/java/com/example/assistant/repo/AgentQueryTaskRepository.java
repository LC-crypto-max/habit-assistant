package com.example.assistant.repo;

import com.example.assistant.model.AgentQueryStatus;
import com.example.assistant.model.AgentQueryTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentQueryTaskRepository extends JpaRepository<AgentQueryTask, Long> {

    Optional<AgentQueryTask> findByTaskId(String taskId);

    Optional<AgentQueryTask> findFirstByStatusOrderByCreatedAtAsc(AgentQueryStatus status);
}
