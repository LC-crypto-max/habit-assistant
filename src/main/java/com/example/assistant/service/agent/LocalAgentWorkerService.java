package com.example.assistant.service.agent;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.AgentWorkerStartRequest;
import com.example.assistant.dto.AgentWorkerStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LocalAgentWorkerService {

    private final AssistantProperties properties;

    public LocalAgentWorkerService(AssistantProperties properties) {
        this.properties = properties;
    }

    public AgentWorkerStartResponse startOnce(AgentWorkerStartRequest request, HttpServletRequest servletRequest) {
        AssistantProperties.LocalWorker localWorker = properties.getLocalWorker();
        if (!localWorker.isEnabled()) {
            throw new IllegalArgumentException("LOCAL_WORKER_DISABLED");
        }
        if (!isLoopback(servletRequest.getRemoteAddr())) {
            throw new IllegalArgumentException("LOCAL_WORKER_LOOPBACK_ONLY");
        }
        if (!isWindows()) {
            throw new IllegalArgumentException("LOCAL_WORKER_WINDOWS_VISIBLE_TERMINAL_ONLY");
        }

        int limit = boundedLimit(request == null ? null : request.limit(), localWorker.getLimit());
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        boolean allowAuthenticatedBrowser = request != null && Boolean.TRUE.equals(request.allowAuthenticatedBrowser());
        boolean confirmedByUser = request != null && Boolean.TRUE.equals(request.confirmedByUser());
        if (allowAuthenticatedBrowser && !confirmedByUser) {
            throw new IllegalArgumentException("BROWSER_AUTHORIZATION_CONFIRMATION_REQUIRED");
        }
        String agentReachMode = agentReachMode(request == null ? null : request.agentReachMode());
        String taskId = request == null ? null : safeTaskId(request.taskId());
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        String commandLine = workerCommand(
                localWorker, limit, dryRun, allowAuthenticatedBrowser, confirmedByUser, agentReachMode, taskId);
        List<String> command = List.of(
                "cmd",
                "/c",
                "start",
                "Habit Assistant Worker",
                "powershell",
                "-NoExit",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                commandLine);

        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
            normalizeWindowsEnvironment(builder);
            builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("LOCAL_WORKER_START_FAILED", exception);
        }

        return new AgentWorkerStartResponse(
                true,
                dryRun ? "dry-run" : "once",
                allowAuthenticatedBrowser
                        ? "已启动本地 PowerShell Worker；本次仅复用现有浏览器登录读取你提交的公开链接。"
                        : "已启动本地 PowerShell Worker，请在窗口中确认授权。",
                command,
                workingDirectory.toString());
    }

    private String workerCommand(AssistantProperties.LocalWorker localWorker, int limit, boolean dryRun,
            boolean allowAuthenticatedBrowser, boolean confirmedByUser, String agentReachMode, String taskId) {
        List<String> args = new ArrayList<>();
        args.add(pythonInvocation(localWorker));
        args.add(quote(localWorker.getScriptPath()));
        args.add("--once");
        args.add("--base-url");
        args.add(quote(localWorker.getBaseUrl()));
        args.add("--limit");
        args.add(String.valueOf(limit));
        args.add("--agent-reach-mode");
        args.add(agentReachMode);
        if (taskId != null) {
            args.add("--task-id");
            args.add(quote(taskId));
        }
        if (allowAuthenticatedBrowser) {
            args.add("--allow-authenticated-browser");
        }
        if (confirmedByUser) {
            args.add("--yes");
        }
        args.add("--verbose");
        if (dryRun) {
            args.add("--dry-run");
        }
        return "$env:PYTHONIOENCODING='utf-8'; "
                + "$env:PYTHONUTF8='1'; "
                + "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
                + "$OutputEncoding=[System.Text.Encoding]::UTF8; "
                + String.join(" ", args);
    }

    private String pythonInvocation(AssistantProperties.LocalWorker localWorker) {
        String configured = localWorker.getPythonCommand();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured.trim())) {
            return configured.trim();
        }
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null && !userProfile.isBlank()) {
            Path codexPython = Path.of(
                    userProfile,
                    ".cache",
                    "codex-runtimes",
                    "codex-primary-runtime",
                    "dependencies",
                    "python",
                    "python.exe").toAbsolutePath().normalize();
            if (Files.isRegularFile(codexPython)) {
                return "& " + quote(codexPython.toString());
            }
        }
        return "py -3";
    }

    private String agentReachMode(String requested) {
        String mode = requested == null ? "auto" : requested.trim().toLowerCase(Locale.ROOT);
        if (!List.of("auto", "live", "off").contains(mode)) {
            throw new IllegalArgumentException("INVALID_AGENT_REACH_MODE");
        }
        return mode;
    }

    private String quote(String value) {
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private String safeTaskId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String taskId = value.trim();
        if (!taskId.matches("query-[A-Za-z0-9-]{16,80}")) {
            throw new IllegalArgumentException("INVALID_AGENT_QUERY_TASK_ID");
        }
        return taskId;
    }

    private int boundedLimit(Integer requested, int configured) {
        int value = requested == null ? configured : requested;
        return Math.max(1, Math.min(value, 100));
    }

    private boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void normalizeWindowsEnvironment(ProcessBuilder builder) {
        if (!isWindows()) {
            return;
        }
        String pathValue = null;
        for (String key : List.copyOf(builder.environment().keySet())) {
            if ("path".equalsIgnoreCase(key)) {
                if (pathValue == null) {
                    pathValue = builder.environment().get(key);
                }
                builder.environment().remove(key);
            }
        }
        if (pathValue != null) {
            builder.environment().put("Path", pathValue);
        }
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.environment().put("PYTHONUTF8", "1");
    }
}
