package com.example.assistant.service.agent;

import com.example.assistant.dto.AgentInterestItemRequest;
import com.example.assistant.dto.AgentQueryCreateRequest;
import com.example.assistant.dto.AgentQueryResultRequest;
import com.example.assistant.dto.AgentQueryResultResponse;
import com.example.assistant.dto.AgentQueryTaskResponse;
import com.example.assistant.dto.AgentTaskRequest;
import com.example.assistant.dto.AgentTaskResponse;
import com.example.assistant.model.AgentQueryStatus;
import com.example.assistant.model.AgentQueryTask;
import com.example.assistant.repo.AgentQueryTaskRepository;
import com.example.assistant.service.UserContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentQueryService {

    private final AgentQueryTaskRepository repository;
    private final AgentTaskService agentTaskService;
    private final UserContext userContext;

    public AgentQueryService(AgentQueryTaskRepository repository, AgentTaskService agentTaskService,
            UserContext userContext) {
        this.repository = repository;
        this.agentTaskService = agentTaskService;
        this.userContext = userContext;
    }

    @Transactional
    public AgentQueryTaskResponse create(AgentQueryCreateRequest request) {
        String userId = userContext.resolve(request.userId());
        String adapter = normalize(firstNonBlank(request.adapter(), "agent-reach"));
        String platform = normalize(request.platform());
        String intent = normalize(request.intent());
        String taskId = "query-" + UUID.randomUUID();
        AgentQueryTask task = new AgentQueryTask(
                taskId,
                userId,
                adapter,
                platform,
                intent,
                blankToNull(request.url()),
                blankToNull(request.query()),
                prompt(userId, adapter, platform, intent, request.url(), request.query()));
        return toResponse(repository.save(task));
    }

    @Transactional
    public AgentQueryTaskResponse claimNext() {
        AgentQueryTask task = repository.findFirstByStatusOrderByCreatedAtAsc(AgentQueryStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("NO_PENDING_AGENT_QUERY"));
        task.markRunning();
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public AgentQueryTaskResponse find(String taskId) {
        return toResponse(findTask(taskId));
    }

    @Transactional
    public AgentQueryResultResponse complete(String taskId, AgentQueryResultRequest request) {
        AgentQueryTask task = findTask(taskId);
        if (Boolean.FALSE.equals(request.success())) {
            task.markFailed(firstNonBlank(request.errorMessage(), "Agent query failed."));
            return new AgentQueryResultResponse(toResponse(task), null);
        }

        AgentTaskResponse ingestion = agentTaskService.process(new AgentTaskRequest(
                task.getUserId(),
                task.getTaskId(),
                task.getAdapter(),
                task.getIntent(),
                task.getPlatform(),
                firstNonBlank(request.summary(), task.getQuery(), task.getUrl()),
                task.getUrl(),
                task.getQuery(),
                request.summary(),
                null,
                request.ingest(),
                safeItems(request.items()),
                metadata(task, request)));
        task.markCompleted();
        return new AgentQueryResultResponse(toResponse(task), ingestion);
    }

    private AgentQueryTask findTask(String taskId) {
        return repository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AGENT_QUERY_NOT_FOUND"));
    }

    private Map<String, Object> metadata(AgentQueryTask task, AgentQueryResultRequest request) {
        if (request.metadata() == null || request.metadata().isEmpty()) {
            return Map.of(
                    "queryTaskId", task.getTaskId(),
                    "privacy", "public-interest-only");
        }
        return request.metadata();
    }

    private List<AgentInterestItemRequest> safeItems(List<AgentInterestItemRequest> items) {
        return items == null ? List.of() : items;
    }

    private AgentQueryTaskResponse toResponse(AgentQueryTask task) {
        return new AgentQueryTaskResponse(
                task.getTaskId(),
                task.getUserId(),
                task.getAdapter(),
                task.getPlatform(),
                task.getIntent(),
                task.getUrl(),
                task.getQuery(),
                task.getPrompt(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                task.getFinishedAt(),
                task.getErrorMessage());
    }

    private String prompt(String userId, String adapter, String platform, String intent, String url, String query) {
        return """
                You are collecting public, non-private interest data for Habit Assistant.
                Return JSON only, using this shape:
                {"items":[{"platform":"%s","eventType":"VISIT","source":"codex-cli-analysis","externalId":"","title":"","url":"","author":"","summary":"","tags":[],"contentType":"","interestCategory":"","confidence":"HIGH","dataLevel":"PAGE_VISIBLE_CONTENT","detectionReason":"public_url_enrichment","rawEvidence":{},"occurredAt":""}]}

                Rules:
                - Return only public metadata fields and privacy-safe summaries.
                - Exclude credentials, account secrets, private conversations, forms, and raw personal records.
                - Prefer public title, public URL, public author/channel, short summary, and tags.
                - Use WATCH for watched videos, FAVORITE for explicit saved/favorite content, VISIT for read pages, SEARCH for search results.

                Context:
                userId=%s
                adapter=%s
                platform=%s
                intent=%s
                url=%s
                query=%s
                """.formatted(platform, userId, adapter, platform, intent, firstNonBlank(url, ""), firstNonBlank(query, ""));
    }

    private String normalize(String value) {
        return firstNonBlank(value, "").toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
