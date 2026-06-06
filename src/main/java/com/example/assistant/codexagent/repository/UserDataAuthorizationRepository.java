package com.example.assistant.codexagent.repository;

import com.example.assistant.codexagent.entity.UserDataAuthorization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDataAuthorizationRepository extends JpaRepository<UserDataAuthorization, String> {

    Optional<UserDataAuthorization> findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(String userId);
}
