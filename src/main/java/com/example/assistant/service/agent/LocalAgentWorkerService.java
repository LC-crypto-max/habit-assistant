package com.example.assistant.service.agent;

import com.example.assistant.config.AssistantProperties;
import com.example.assistant.dto.AgentWorkerStartRequest;
import com.example.assistant.dto.AgentWorkerStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        String commandLine = workerCommand(localWorker, limit, dryRun);
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
            new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .start();
        } catch (IOException ex) {
            throw new IllegalStateException("LOCAL_WORKER_START_FAILED: " + ex.getMessage(), ex);
        }

        return new AgentWorkerStartResponse(
                true,
                dryRun ? "dry-run" : "once",
                "已打开本地 PowerShell worker 窗口，请在窗口中查看任务并输入 y 授权执行。",
                command,
                workingDirectory.toString());
    }

    private String workerCommand(AssistantProperties.LocalWorker localWorker, int limit, boolean dryRun) {
        List<String> args = new ArrayList<>();
        args.add(localWorker.getPythonCommand());
        args.add(quote(localWorker.getScriptPath()));
        args.add("--once");
        args.add("--base-url");
        args.add(quote(localWorker.getBaseUrl()));
        args.add("--limit");
        args.add(String.valueOf(limit));
        if (dryRun) {
            args.add("--dry-run");
        }
        return "$env:PYTHONIOENCODING='utf-8'; " + String.join(" ", args);
    }

    private String quote(String value) {
        return "'" + String.valueOf(value).replace("'", "''") + "'";
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
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
