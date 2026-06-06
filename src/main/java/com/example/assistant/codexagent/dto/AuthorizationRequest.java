package com.example.assistant.codexagent.dto;

import com.example.assistant.codexagent.policy.DataAccessScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;

public record AuthorizationRequest(
        @NotBlank String userId,
        @NotEmpty List<DataAccessScope> grantedScopes,
        List<String> allowedApps,
        List<String> allowedDomains,
        List<String> allowedPaths,
        PrivacyOptions privacy,
        OffsetDateTime expireAt) {

    public record PrivacyOptions(
            Boolean sanitize,
            Boolean keepRawText,
            Boolean allowSensitiveData) {
    }
}
