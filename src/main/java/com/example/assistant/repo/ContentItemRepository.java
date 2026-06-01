package com.example.assistant.repo;

import com.example.assistant.model.ContentItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {

    Optional<ContentItem> findByContentHash(String contentHash);
}
