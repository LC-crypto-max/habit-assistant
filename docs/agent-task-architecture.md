# Agent Task Architecture

This project now has a dedicated boundary for Codex CLI / Agent Reach style
workflows:

```text
Task JSON
  -> Codex CLI or TRAE CLI
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

The adapter now executes the channel commands documented by Agent Reach. Run a
local, non-sensitive diagnostic first:

```text
python scripts/agent_reach_adapter.py --doctor
```

Routing:

- `bilibili` -> `bili video BV...`
- `youtube` -> `yt-dlp --dump-single-json --skip-download ...`
- `xiaohongshu` -> `opencli xiaohongshu note URL -f json` (only with
  `--allow-authenticated-browser`)
- public web/GitHub/article URLs -> Jina Reader through `curl`

Only a successful tool read is marked `source=agent-reach-enrichment`,
`dataLevel=PAGE_VISIBLE_CONTENT`, and `confidence=HIGH`. A missing/failed tool
keeps the original browser-history confidence and records a sanitized fallback
status. Command arguments and login credentials are never persisted. URLs are
stored without tracking or sensitive query parameters such as `xsec_token`.

Verbose worker tracing:

```powershell
$demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $demoPython scripts/codex_query_worker.py --once --yes --direct-behavior-batch --verbose
```

For a supplied Xiaohongshu public note URL, explicitly allow local OpenCLI to
reuse the existing browser login:

```powershell
& $demoPython scripts/codex_query_worker.py --once --direct-behavior-batch `
  --agent-reach-mode live --allow-authenticated-browser
```

The worker never exports that login state. It waits for Codex semantic analysis
before posting the normalized `behavior_event`, so tags and summaries are
available to the profile builder immediately.

## TRAE CLI Provider

Use `--analysis-provider trae` to run the same public-content semantic analysis through TRAE CLI. The provider invokes TRAE in a fresh temporary working directory and passes only the sanitized JSON prompt produced after Agent Reach enrichment and PolicyGate evaluation. Codex remains available with `--analysis-provider codex`.

The normalized source records which provider completed the analysis:

- `trae-cli-analysis`
- `codex-cli-analysis`

Both providers must return strict JSON containing `summary`, `tags`, `interestCategory`, `intent`, and `confidence`. Invalid, sensitive, or non-JSON output is rejected and the original public metadata is retained.

## Analysis Channels

`--analysis-provider` selects the semantic engine, while `--analysis-channel` selects the evidence source:

- `agent-reach`: platform-aware public-content enrichment followed by TRAE or Codex analysis.
- `public-metadata`: sanitized URL/title metadata only, used as a safe fallback.

For the Xiaohongshu demo, the supported route is `Agent Reach/OpenCLI visible public tab -> PolicyGate -> TRAE CLI -> PrivacySanitizer -> ingestion`. This preserves the multi-platform routing described by Agent Reach while ensuring that browser credentials and signed query parameters never enter the model context or persistence layer.
