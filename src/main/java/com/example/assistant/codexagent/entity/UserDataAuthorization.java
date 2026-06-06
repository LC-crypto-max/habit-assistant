package com.example.assistant.codexagent.entity;

import com.example.assistant.codexagent.dto.AuthorizationRequest;
import com.example.assistant.codexagent.policy.DataAccessScope;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UserDataAuthorization {

    @Id
    private String authorizationId;

    private String userId;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<DataAccessScope> grantedScopes = new ArrayList<>();

    @ElementCollection
    private List<String> allowedApps = new ArrayList<>();

    @ElementCollection
    private List<String> allowedDomains = new ArrayList<>();

    @ElementCollection
    private List<String> allowedPaths = new ArrayList<>();

    private boolean sanitize;
    private boolean keepRawText;
    private boolean allowSensitiveData;
    private boolean revoked;
    private OffsetDateTime expireAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;

    protected UserDataAuthorization() {
    }

    public UserDataAuthorization(String authorizationId, String userId, List<DataAccessScope> grantedScopes,
            List<String> allowedApps, List<String> allowedDomains, List<String> allowedPaths,
            AuthorizationRequest.PrivacyOptions privacy, OffsetDateTime expireAt) {
        this.authorizationId = authorizationId;
        this.userId = userId;
        this.grantedScopes = grantedScopes == null ? new ArrayList<>() : new ArrayList<>(grantedScopes);
        this.allowedApps = allowedApps == null ? new ArrayList<>() : new ArrayList<>(allowedApps);
        this.allowedDomains = allowedDomains == null ? new ArrayList<>() : new ArrayList<>(allowedDomains);
        this.allowedPaths = allowedPaths == null ? new ArrayList<>() : new ArrayList<>(allowedPaths);
        this.sanitize = privacy == null || !Boolean.FALSE.equals(privacy.sanitize());
        this.keepRawText = privacy != null && Boolean.TRUE.equals(privacy.keepRawText());
        this.allowSensitiveData = false;
        this.expireAt = expireAt;
        this.createdAt = OffsetDateTime.now();
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    public String getUserId() {
        return userId;
    }

    public List<DataAccessScope> getGrantedScopes() {
        return grantedScopes;
    }

    public List<String> getAllowedApps() {
        return allowedApps;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public boolean isSanitize() {
        return sanitize;
    }

    public boolean isKeepRawText() {
        return keepRawText;
    }

    public boolean isAllowSensitiveData() {
        return allowSensitiveData;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public OffsetDateTime getExpireAt() {
        return expireAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isExpired() {
        return expireAt != null && expireAt.isBefore(OffsetDateTime.now());
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = OffsetDateTime.now();
    }
}
