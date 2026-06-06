package com.example.assistant.codexagent.service;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.dto.AuthorizationRequest;
import com.example.assistant.codexagent.dto.AuthorizationResponse;
import com.example.assistant.codexagent.dto.RevokeAuthorizationRequest;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import com.example.assistant.codexagent.policy.DataPolicyChecker;
import com.example.assistant.codexagent.policy.PolicyCheckResult;
import com.example.assistant.codexagent.storage.JsonFileStore;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserDataAuthorizationService {

    private final CodexAgentProperties properties;
    private final JsonFileStore store;
    private final DataPolicyChecker policyChecker;

    public UserDataAuthorizationService(CodexAgentProperties properties, JsonFileStore store,
            DataPolicyChecker policyChecker) {
        this.properties = properties;
        this.store = store;
        this.policyChecker = policyChecker;
    }

    public AuthorizationResponse grant(AuthorizationRequest request) {
        PolicyCheckResult textCheck = policyChecker.checkText(String.join(" ",
                String.valueOf(request.allowedApps()),
                String.valueOf(request.allowedDomains()),
                String.valueOf(request.allowedPaths())));
        if (!textCheck.allowed()) {
            return rejected(textCheck);
        }
        OffsetDateTime expireAt = request.expireAt() == null
                ? OffsetDateTime.now().plusDays(properties.getAuthorization().getDefaultExpireDays())
                : request.expireAt();
        AuthorizationRequest.PrivacyOptions privacy = request.privacy() == null
                ? new AuthorizationRequest.PrivacyOptions(true, false, false)
                : new AuthorizationRequest.PrivacyOptions(
                        !Boolean.FALSE.equals(request.privacy().sanitize()),
                        Boolean.TRUE.equals(request.privacy().keepRawText()),
                        false);
        UserDataAuthorization authorization = new UserDataAuthorization(
                UUID.randomUUID().toString(),
                request.userId(),
                request.grantedScopes(),
                safeList(request.allowedApps(), properties.getSecurity().getAllowedApps()),
                safeList(request.allowedDomains(), properties.getSecurity().getAllowedDomains()),
                safeList(request.allowedPaths(), properties.getSecurity().getAllowedPaths()),
                privacy,
                expireAt);
        List<UserDataAuthorization> all = readAll();
        all.add(authorization);
        store.write(authorizationsPath(), all);
        return toResponse("SUCCESS", authorization, "授权成功。系统只会采集授权范围内的非敏感访问行为数据。", "", "");
    }

    public AuthorizationResponse current(String userId) {
        return readAll().stream()
                .filter(item -> item.getUserId().equals(userId))
                .filter(item -> !item.isRevoked())
                .max(Comparator.comparing(UserDataAuthorization::getCreatedAt))
                .map(item -> toResponse("SUCCESS", item, "当前授权有效。", "", ""))
                .orElse(new AuthorizationResponse("REJECTED", "", userId, List.of(), List.of(), List.of(), List.of(),
                        null, null, "", "用户未授权。", "请先调用授权接口。"));
    }

    public UserDataAuthorization currentAuthorization(String userId) {
        return readAll().stream()
                .filter(item -> item.getUserId().equals(userId))
                .filter(item -> !item.isRevoked())
                .max(Comparator.comparing(UserDataAuthorization::getCreatedAt))
                .orElse(null);
    }

    public AuthorizationResponse revoke(RevokeAuthorizationRequest request) {
        List<UserDataAuthorization> all = readAll();
        for (UserDataAuthorization item : all) {
            if (item.getUserId().equals(request.userId())
                    && item.getAuthorizationId().equals(request.authorizationId())) {
                item.revoke();
                store.write(authorizationsPath(), all);
                return toResponse("SUCCESS", item, "授权已撤销。", "", "");
            }
        }
        return new AuthorizationResponse("REJECTED", request.authorizationId(), request.userId(), List.of(), List.of(),
                List.of(), List.of(), null, null, "", "授权记录不存在。", "请检查 authorizationId。");
    }

    private List<UserDataAuthorization> readAll() {
        return store.readList(authorizationsPath(), new TypeReference<List<UserDataAuthorization>>() {
        });
    }

    private Path authorizationsPath() {
        return Path.of(properties.getStorage().getProcessedPath(), "authorizations.json");
    }

    private List<String> safeList(List<String> requested, List<String> defaults) {
        return requested == null || requested.isEmpty() ? defaults : requested;
    }

    private AuthorizationResponse rejected(PolicyCheckResult result) {
        return new AuthorizationResponse("REJECTED", "", "", List.of(), List.of(), List.of(), List.of(), null, null,
                "", result.reason(), result.safeAlternative());
    }

    private AuthorizationResponse toResponse(String status, UserDataAuthorization authorization, String message,
            String reason, String safeAlternative) {
        return new AuthorizationResponse(
                status,
                authorization.getAuthorizationId(),
                authorization.getUserId(),
                authorization.getGrantedScopes(),
                authorization.getAllowedApps(),
                authorization.getAllowedDomains(),
                authorization.getAllowedPaths(),
                new AuthorizationRequest.PrivacyOptions(
                        authorization.isSanitize(),
                        authorization.isKeepRawText(),
                        authorization.isAllowSensitiveData()),
                authorization.getExpireAt(),
                message,
                reason,
                safeAlternative);
    }
}
