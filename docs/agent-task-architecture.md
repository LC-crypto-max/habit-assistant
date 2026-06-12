# Agent Task Architecture

This project now has a dedicated boundary for Codex CLI / Agent Reach style
workflows:

```text
Task JSON
  -> Codex Agent / Codex CLI
  -> local script or skill
  -> data source adapter
  -> non-private interest data
  -> structured JSON returned to Habit Assistant
```

## Backend Boundary

`POST /api/agent/tasks` is the project-facing boundary for agent results. The
endpoint accepts normalized agent output and converts it into existing behavior
events, so profile learning and recommendations continue to use the current
pipeline.

Key classes:

- `AgentTaskController`: REST boundary for task JSON.
- `AgentTaskService`: adapts agent result items into non-private behavior
  events.
- `BehaviorEventService`: existing ingestion and optional message publishing.
- `ActivityService` / `ProfileService`: existing storage and interest learning.

## Request Shape

```json
{
  "userId": "alice",
  "taskId": "bili-video-summary-001",
  "adapter": "agent-reach",
  "intent": "read-video",
  "ingest": true,
  "items": [
    {
      "platform": "bilibili",
      "type": "WATCH",
      "externalId": "BV1demo",
      "title": "Bilibili Spring Boot recommendation video",
      "url": "https://www.bilibili.com/video/BV1demo",
      "summary": "Non-private summary produced by the local agent.",
      "tags": ["bilibili", "java", "recommendation"]
    }
  ],
  "metadata": {
    "sourceUrl": "https://www.bilibili.com/video/BV1demo"
  }
}
```

Set `ingest` to `false` to preview the normalized events without writing them to
the database.

## Privacy Rule

The endpoint is intentionally designed to receive only non-private interest
signals: title, public URL, public author, public summary, task intent, and tags.
Local scripts or skills should strip cookies, tokens, browser history internals,
private comments, and raw account data before calling this API.

## Agent Reach Adapter

Worker-side public URL enrichment uses `scripts/agent_reach_adapter.py`.

Public entry point:

```python
enrich_public_url(item: dict) -> dict
```

Phase 1 runs in mock/dry-run mode and does not perform network access. It prints
the command that a future real adapter can execute:

```text
[AgentReach] 即将执行命令: agent-reach read --url "<url>" --platform "<platform>" --dry-run
[AgentReach] 返回结果: {...}
```

Routing:

- `bilibili` -> `read_bilibili(url)`
- `youtube` -> `read_youtube(url)`
- `xiaohongshu` -> `read_xiaohongshu_public(url)`
- `github` -> `read_github(url)`
- `web` -> `read_web_page(url)`

The adapter returns normalized, non-private behavior data with
`source=agent-reach-enrichment`, `dataLevel=PAGE_VISIBLE_CONTENT`, and
`confidence=HIGH`. Raw evidence is sanitized and limited to safe metadata such as
domain, visit count, adapter mode, and the dry-run command string.

Verbose worker tracing:

```powershell
py -3 scripts/codex_query_worker.py --once --yes --use-codex-cli --direct-behavior-batch --verbose
```

`--verbose` prints the current task, collected browser/public URL items, Agent
Reach dry-run command and output, Codex CLI command/input/output, final
`behavior_event` JSON, and the backend POST URL plus response.
