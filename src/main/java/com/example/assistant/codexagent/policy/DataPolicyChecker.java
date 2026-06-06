package com.example.assistant.codexagent.policy;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import com.example.assistant.codexagent.entity.UserDataAuthorization;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DataPolicyChecker {

    private static final String SENSITIVE_REASON = "该任务涉及敏感数据或受保护数据，不能执行。";

    private final CodexAgentProperties properties;

    public DataPolicyChecker(CodexAgentProperties properties) {
        this.properties = properties;
    }

    public PolicyCheckResult checkText(String value) {
        if (value == null || value.isBlank()) {
            return PolicyCheckResult.ok();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String keyword : properties.getSecurity().getBlockedKeywords()) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return PolicyCheckResult.rejected(SENSITIVE_REASON);
            }
        }
        return PolicyCheckResult.ok();
    }

    public PolicyCheckResult checkSourceAllowed(UserDataAuthorization authorization, DataAccessScope source) {
        if (authorization == null || authorization.isRevoked() || authorization.isExpired()) {
            return PolicyCheckResult.rejected("用户未授权或授权已失效。");
        }
        if (!authorization.getGrantedScopes().contains(source)) {
            return PolicyCheckResult.rejected("请求的数据源不在用户授权范围内。");
        }
        return PolicyCheckResult.ok();
    }

    public PolicyCheckResult checkPath(String path, Collection<String> allowedPaths) {
        PolicyCheckResult sensitive = checkText(path);
        if (!sensitive.allowed()) {
            return sensitive;
        }
        try {
            Path target = Path.of(path).toAbsolutePath().normalize();
            for (String allowed : allowedPaths) {
                Path allowedPath = Path.of(allowed).toAbsolutePath().normalize();
                if (target.startsWith(allowedPath)) {
                    return PolicyCheckResult.ok();
                }
            }
            return PolicyCheckResult.rejected("路径不在授权目录内。");
        } catch (Exception e) {
            return PolicyCheckResult.rejected("路径不合法。");
        }
    }

    public PolicyCheckResult checkDomain(String url, Collection<String> allowedDomains) {
        PolicyCheckResult sensitive = checkText(url);
        if (!sensitive.allowed()) {
            return sensitive;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return PolicyCheckResult.rejected("URL 缺少域名。");
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            for (String domain : allowedDomains) {
                String allowed = domain.toLowerCase(Locale.ROOT);
                if (normalized.equals(allowed) || normalized.endsWith("." + allowed)) {
                    return PolicyCheckResult.ok();
                }
            }
            return PolicyCheckResult.rejected("URL 域名不在授权范围内。");
        } catch (Exception e) {
            return PolicyCheckResult.rejected("URL 不合法。");
        }
    }
}
