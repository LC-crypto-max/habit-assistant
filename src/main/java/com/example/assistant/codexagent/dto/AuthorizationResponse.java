package com.example.assistant.codexagent.dto;

import com.example.assistant.codexagent.policy.DataAccessScope;
import java.time.OffsetDateTime;
import java.util.List;

public record AuthorizationResponse(
        String status,
        String authorizationId,
        String userId,
        List<DataAccessScope> grantedScopes,
        List<String> allowedApps,
        List<String> allowedDomains,
        List<String> allowedPaths,
        AuthorizationRequest.PrivacyOptions privacy,
        OffsetDateTime expireAt,
        String message,
        String reason,
        String safeAlternative) {
}
