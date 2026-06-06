package com.example.assistant.codexagent.sanitizer;

import com.example.assistant.codexagent.config.CodexAgentProperties;
import org.springframework.stereotype.Component;

@Component
public class PrivacySanitizer {

    private final CodexAgentProperties properties;

    public PrivacySanitizer(CodexAgentProperties properties) {
        this.properties = properties;
    }

    public String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        result = result.replaceAll("(?i)authorization\\s*:\\s*bearer\\s+[^\\s,;]+", "[REDACTED_AUTHORIZATION]");
        result = result.replaceAll("(?i)bearer\\s+[^\\s,;]+", "[REDACTED_AUTHORIZATION]");
        result = result.replaceAll("(?i)(token\\s*[=:]\\s*)[^\\s,;&]+", "$1[REDACTED_TOKEN]");
        result = result.replaceAll("(?i)(cookie\\s*[=:]\\s*)[^\\n;]+", "$1[REDACTED_COOKIE]");
        result = result.replaceAll("(?i)(session\\s*[=:]\\s*)[^\\s,;&]+", "$1[REDACTED_SESSION]");
        result = result.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
        result = result.replaceAll("([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})", "$1***@$3");
        result = result.replaceAll("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)", "$1********$2");
        result = result.replaceAll("(?<!\\d)(\\d{4})[ -]?\\d{4}[ -]?\\d{4}[ -]?(\\d{4})(?!\\d)", "$1 **** **** $2");
        result = result.replaceAll("(地址|住址|收货地址|家庭住址)[:：]?\\s*[^,，。\\n]+", "$1：[REDACTED_ADDRESS]");
        int max = properties.getPrivacy().getMaxTextLength();
        if (max > 0 && result.length() > max) {
            result = result.substring(0, max);
        }
        return result;
    }
}
