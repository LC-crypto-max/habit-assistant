package com.example.assistant.codexagent.policy;

public record PolicyCheckResult(
        boolean permitted,
        String reason,
        String safeAlternative) {

    public boolean allowed() {
        return permitted;
    }

    public static PolicyCheckResult ok() {
        return new PolicyCheckResult(true, "", "");
    }

    public static PolicyCheckResult rejected(String reason) {
        return new PolicyCheckResult(false, reason,
                "可以改为采集 App 使用概况、浏览器历史标题与 URL、公开链接元数据或本地笔记。");
    }
}
