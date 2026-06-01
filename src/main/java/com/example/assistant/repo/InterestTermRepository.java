package com.example.assistant.repo;

import com.example.assistant.model.InterestTerm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestTermRepository extends JpaRepository<InterestTerm, Long> {

    Optional<InterestTerm> findByUserIdAndTerm(String userId, String term);

    List<InterestTerm> findTop20ByUserIdOrderByWeightDescLastSeenAtDesc(String userId);
}
