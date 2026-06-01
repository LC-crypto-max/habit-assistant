package com.example.assistant.service.collector;

import java.util.List;

public interface PlatformCollector {

    String platform();

    List<CollectedContent> search(String userId, List<String> interestTerms);
}
