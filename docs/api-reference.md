## Demo System Status

### GET /api/v1/demo-system/status

使用 JDBC metadata 返回当前实际数据源，不包含连接 URL、账号或密码。Vue 同时检查该接口与
`/actuator/health`，避免把 H2 回退模式误显示为 MySQL。

```json
{
  "ready": true,
  "database": "MySQL",
  "databaseVersion": "8.0.46",
  "profile": "mysql",
  "message": "本地数据库连接正常。"
}
```

## Xiaohongshu Demo Snapshot

### GET /api/v1/demo-snapshots/xiaohongshu

返回小红书现场演示所需的只读聚合快照，包括最近访问记录、Agent Reach/Codex 执行状态、
小红书 24 小时信号汇总、v2 用户画像和今日推荐。

Query:

| Param | Required | Description |
| --- | --- | --- |
| `userId` | No | 用户标识；开启鉴权后以后端会话用户为准 |

`readiness` 中的五项状态必须全部为 `true`，`ready` 才会为 `true`。安全入库的执行证据包括
`adapterMode`、`agentReachStatus/Route/Backend`、`llmStatus` 和 `llmLatencyMs`；命令原文、
Cookie、Token、Session 与错误敏感上下文不会返回。

```json
{
  "userId": "xhs-demo",
  "generatedAt": "2026-07-22T10:30:00",
  "readiness": {
    "ready": true,
    "visitCaptured": true,
    "agentReachLive": true,
    "aiAnalyzed": true,
    "profileReady": true,
    "recommendationsReady": true,
    "hints": ["演示链路已就绪：访问记录、Agent Reach、Codex 分析、画像和推荐均有结果。"]
  },
  "degraded": false,
  "components": [
    {"component": "usage", "status": "UP", "message": "数据读取成功"},
    {"component": "visits", "status": "UP", "message": "数据读取成功"},
    {"component": "profile", "status": "UP", "message": "数据读取成功"},
    {"component": "recommendations", "status": "UP", "message": "数据读取成功"}
  ],
  "visits": [
    {
      "title": "公开笔记标题",
      "url": "https://www.xiaohongshu.com/explore/example",
      "summary": "Codex 生成的非敏感语义摘要",
      "agentReachStatus": "SUCCESS",
      "agentReachBackend": "opencli",
      "llmStatus": "SUCCESS",
      "llmLatencyMs": 812,
      "interestCategory": "AI效率工具"
    }
  ]
}
```

## Local Agent Worker

### POST /api/agent/worker/start-once

仅允许从本机 loopback 地址调用。在 Windows 上打开一个可见 PowerShell 窗口，运行一次
Python worker。Vue 已完成明确单次授权时，`confirmedByUser=true` 会避免终端重复确认；
PowerShell 仍展示完整阶段日志。接口不会接收 Cookie、Token、Session 或账号密码。

```json
{
  "dryRun": false,
  "limit": 20,
  "allowAuthenticatedBrowser": true,
  "agentReachMode": "live",
  "confirmedByUser": true,
  "taskId": "query-12345678-1234-1234-1234-123456789abc"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `dryRun` | No | `true` 时只分析和打印，不回写后端 |
| `limit` | No | 本次最多领取的任务数，服务端限制为 1–100 |
| `allowAuthenticatedBrowser` | No | 显式允许本次复用现有浏览器登录会话读取用户提交的小红书公开链接；默认 `false` |
| `agentReachMode` | No | `auto`、`live` 或 `off`；小红书真实演示使用 `live` |
| `confirmedByUser` | No | 表示本次请求已在 Vue 页面完成明确授权；读取登录浏览器时必须为 `true` |
| `taskId` | No | 仅领取该任务；Vue 演示必须传入刚创建的任务编号，避免处理旧任务 |
| `analysisProvider` | No | `trae` 或 `codex`；小红书主演示使用 `trae` |
| `analysisChannel` | No | `agent-reach` 或 `public-metadata`；主演示使用 `agent-reach` |

`python-command: auto` 会优先使用 Codex Desktop 已安装的 Python 运行时；其他环境可通过
`assistant.local-worker.python-command` 显式指定可信 Python 命令。

## Daily Profile

### GET /api/profile/daily

输出适合 HTML 网页、微信小程序或飞书机器人渲染的每日画像 JSON。

Query:

| Param | Required | Description |
| --- | --- | --- |
| `userId` | No | 用户标识，未传时使用默认用户 |
| `refresh` | No | `true` 时跳过缓存重新生成 |

Response:

```json
{
  "userId": "alice",
  "profileDate": "2026-06-04",
  "generatedAt": "2026-06-04T10:00:00",
  "summary": "用户 alice 最近 7 天产生 12 条行为，30 天累计 40 条。当前主要兴趣集中在 AI、Java、推荐系统。",
  "last7Days": {
    "days": 7,
    "activityCount": 12,
    "typeCounts": {"WATCH": 6, "SEARCH": 4},
    "platformCounts": {"bilibili": 5, "xiaohongshu": 4},
    "tags": [
      {"term": "AI", "weight": 10.5, "hitCount": 3, "lastSeenAt": "2026-06-04T09:00:00"}
    ]
  },
  "last30Days": {
    "days": 30,
    "activityCount": 40,
    "typeCounts": {},
    "platformCounts": {},
    "tags": []
  },
  "topTags": [],
  "recentActivities": [],
  "cached": false
} 
```

## Behavior Event Batch Compatibility

`POST /api/v1/behavior-events/batch` accepts standard behavior event JSON with:

- Required fields: `events[].userId`, `events[].platform`, and event type.
- Event type can be sent as `type` or `eventType`.
- Event type values are case-insensitive and may use `-` instead of `_`, for example `visit` or `app-usage`.
- `events[].tags` accepts a JSON string array.
- `events[].rawMetadata` or `events[].rawEvidence` accepts a JSON object and is stored as sanitized JSON text.
- `events[].occurredAt` accepts ISO-8601 local or offset datetime strings, for example `2026-06-12T10:00:00` or `2026-06-12T10:00:00+08:00`; `createdAt` is accepted as an alias.
- Standard worker events use `eventType`, `contentSnippet`, `rawMetadata`, and `interestCategory`; legacy `type`, `summary`, `rawEvidence`, and `contentCategory` are still accepted.
- Supported normalized platforms are `youtube`, `bilibili`, `baidu`, `xiaohongshu`, and `web`; `baidu_search` normalizes to `baidu`, and `generic_web` normalizes to `web`.

Standard event example:

```json
{
  "events": [
    {
      "userId": "me",
      "eventType": "VISIT",
      "platform": "github",
      "source": "codex-cli-analysis",
      "url": "https://github.com/openai/codex",
      "title": "OpenAI Codex repository",
      "author": "openai",
      "contentSnippet": "Public GitHub repository.",
      "tags": ["github", "codex"],
      "contentType": "repository-or-code-page",
      "interestCategory": "developer-tooling",
      "confidence": "HIGH",
      "dataLevel": "PAGE_VISIBLE_CONTENT",
      "detectionReason": "public_url_enrichment",
      "rawMetadata": {
        "domain": "github.com",
        "adapter": "agent-reach",
        "adapterMode": "live"
      },
      "occurredAt": "2026-06-12T10:00:00"
    }
  ]
}
```

Error responses include the stable JSON shape below while keeping legacy `error` and `fields` members:

```json
{
  "success": false,
  "message": "请求字段校验失败",
  "path": "/api/v1/behavior-events/batch",
  "error": "VALIDATION_ERROR",
  "fields": {
    "events[0].userId": "must not be blank"
  }
}
```

缓存说明：

- 默认 `assistant.profile.cache.redis.enabled=false`，本地测试不依赖 Redis。
- 开启 Redis 后 key 格式为 `profile:daily:{userId}:{yyyy-MM-dd}`。
- `refresh=true` 会重新生成并覆盖当天缓存。

## Recommendation v1

### GET /api/v1/recommendations/today

返回供 HTML 页面直接渲染的今日推荐 JSON。

Query:

| Param | Required | Description |
| --- | --- | --- |
| `userId` | No | 用户标识 |
| `refresh` | No | `true` 时强制刷新今日推荐 |

Response:

```json
{
  "userId": "alice",
  "date": "2026-06-05",
  "generatedAt": "2026-06-05T09:00:00",
  "summary": "今日为用户 alice 生成 10 条推荐。",
  "refreshPolicy": {
    "userId": "alice",
    "recentActivityCount": 12,
    "refreshHours": 6,
    "expired": false
  },
  "profileTags": [
    {"term": "ai", "weight": 12.5, "hitCount": 4, "lastSeenAt": "2026-06-05T08:00:00"}
  ],
  "items": [
    {
      "id": 1,
      "contentId": 10,
      "platform": "douyin",
      "title": "抖音 AI 工具短视频",
      "url": "https://www.douyin.com/search/AI%20效率%20工具",
      "summary": "内容摘要",
      "tags": ["douyin", "ai", "效率"],
      "score": 18.4,
      "reason": "命中你的兴趣词：ai、效率",
      "feedback": "NONE",
      "matchedTags": ["ai", "效率"],
      "actions": {
        "click": "/api/v1/recommendations/1/click",
        "notInterested": "/api/v1/recommendations/1/not-interested",
        "feedback": "/api/v1/recommendations/1/feedback"
      }
    }
  ]
}
```

### POST /api/v1/recommendations/{id}/click

记录用户点击。系统会把反馈转换为 `READ`，并写入一条正向用户行为，使相关平台和标签获得后续推荐加权。

### POST /api/v1/recommendations/{id}/not-interested

记录“不感兴趣”。系统会把反馈转换为 `DISLIKE`，并写入一条负向用户行为，使相关标签权重降低。

### POST /api/v1/recommendations/{id}/feedback

通用反馈接口。

Request:

```json
{
  "feedback": "LIKE"
}
```

## Douyin Data Source

抖音数据源统一使用：

```text
platform = douyin
```

推荐接口：

```text
POST /api/v1/behavior-events/batch
```

Request:

```json
{
  "events": [
    {
      "userId": "alice",
      "platform": "douyin",
      "source": "manual-export",
      "externalId": "douyin-aweme-001",
      "type": "WATCH",
      "title": "抖音 AI 工具短视频收藏",
      "url": "https://www.douyin.com/search/AI%20效率%20工具",
      "summary": "用户主动上传的抖音观看记录",
      "tags": ["抖音", "AI", "效率", "短视频"]
    }
  ]
}
```

提交后可通过 `GET /api/profile/daily?userId=alice&refresh=true` 查看 `last7Days.platformCounts.douyin` 和兴趣标签权重。

## Behavior Events

### POST /api/v1/behavior-events/batch

批量接收小红书、微信、B站等采集端上传的用户行为事件。接口会将事件转换为系统内部 `UserActivity`，写入当前数据源；当应用使用 MySQL profile 时会落入 MySQL。接口还会调用 `BehaviorEventPublisher`，在 RabbitMQ 开启时发送异步消息，供画像和推荐服务后续消费。

Request:

```json
{
  "events": [
    {
      "userId": "alice",
      "platform": "xiaohongshu",
      "source": "agent-reach",
      "externalId": "xhs-note-001",
      "type": "FAVORITE",
      "title": "小红书 AI 工作流笔记",
      "url": "https://www.xiaohongshu.com/explore/demo-note",
      "author": "作者名",
      "summary": "用户收藏的小红书效率笔记",
      "text": "补充备注",
      "occurredAt": "2026-06-04T09:00:00",
      "tags": ["小红书", "AI", "效率"]
    },
    {
      "userId": "alice",
      "platform": "wechat",
      "source": "wechat-mini",
      "externalId": "wechat-link-001",
      "type": "VISIT",
      "title": "微信公众号阅读清单",
      "url": "https://mp.weixin.qq.com/s/demo",
      "tags": ["微信", "阅读"]
    },
    {
      "userId": "alice",
      "platform": "bilibili",
      "source": "agent-reach",
      "externalId": "BV1demo",
      "type": "WATCH",
      "title": "B站 Spring Boot 推荐系统视频",
      "url": "https://www.bilibili.com/video/BV1demo",
      "tags": ["B站", "Java", "推荐系统"]
    }
  ]
}
```

Response:

```json
{
  "imported": 3,
  "skipped": 0,
  "messagesPublished": 0,
  "activities": [
    {
      "id": 1,
      "type": "FAVORITE",
      "platform": "xiaohongshu",
      "title": "小红书 AI 工作流笔记",
      "url": "https://www.xiaohongshu.com/explore/demo-note",
      "tags": ["xiaohongshu", "小红书", "AI", "效率"]
    }
  ]
}
```

字段说明：

| Field | Required | Description |
| --- | --- | --- |
| `events` | Yes | 批量事件数组，不能为空 |
| `events[].platform` | Yes | 平台标识，如 `xiaohongshu`、`wechat`、`bilibili` |
| `events[].type` | No | `SEARCH`、`VISIT`、`WATCH`、`LIKE`、`FAVORITE`、`DISLIKE`；为空时按 URL 推断 |
| `events[].source` | No | 采集来源，如 `agent-reach`、`wechat-mini`、`feishu-bot` |
| `events[].externalId` | No | 外部平台内容 ID，用于幂等扩展和追踪 |
| `events[].occurredAt` | No | 行为发生时间，未传则由服务端补当前时间 |

RabbitMQ 默认关闭，本地测试时 `messagesPublished` 通常为 `0`。生产环境开启：

```yaml
assistant:
  behavior:
    rabbitmq:
      enabled: true
      exchange: habit.behavior.events
      routing-key: behavior.events.created
```

# API Reference

## Base URL

本地直接访问：

```text
http://localhost:8080
```

通过 Nginx 访问：

```text
http://localhost:8088
```

## Error Format

参数校验失败：

```json
{
  "error": "VALIDATION_ERROR",
  "fields": {
    "fieldName": "must not be blank"
  }
}
```

JSON 或日期格式错误：

```json
{
  "error": "INVALID_REQUEST_BODY",
  "message": "请求体格式不正确，请检查 JSON 和日期格式"
}
```

## Habit Submission

### POST /api/habits/submit

提交一个小规模协作场景下的习惯打卡记录。

Request:

```json
{
  "nickname": "小李",
  "habitName": "阅读打卡",
  "content": "今天阅读了 Spring Boot 部署文档",
  "recordDate": "2026-05-31",
  "remark": "通过 Nginx 页面提交"
}
```

Validation:

| Field | Required | Limit |
| --- | --- | --- |
| `nickname` | Yes | 40 chars |
| `habitName` | Yes | 80 chars |
| `content` | Yes | 500 chars |
| `recordDate` | Yes | `yyyy-MM-dd` |
| `remark` | No | 300 chars |

Response:

```json
{
  "success": true,
  "message": "提交成功",
  "data": {
    "id": 1,
    "nickname": "小李",
    "habitName": "阅读打卡",
    "content": "今天阅读了 Spring Boot 部署文档",
    "recordDate": "2026-05-31",
    "remark": "通过 Nginx 页面提交",
    "createdAt": "2026-05-31T20:00:00"
  }
}
```

## Profile

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/profile/users` | List users found in activity data |
| `GET` | `/api/profile?userId=alice` | Get a user's profile terms |

## Activities

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/search-terms` | Record a search term |
| `POST` | `/api/activities` | Record a generic behavior |
| `POST` | `/api/datasources/events` | Record one normalized source event |
| `POST` | `/api/datasources/events/batch` | Record normalized source events in batch |
| `POST` | `/api/datasources/{platform}/events` | Record one event with platform from path |
| `POST` | `/api/agent/tasks` | Normalize Codex/Agent Reach task results and optionally ingest them |
| `POST` | `/api/agent/queries` | Create an agent query task for a local Codex/Agent Reach worker |
| `POST` | `/api/agent/queries/claim-next` | Claim the oldest pending agent query task |
| `GET` | `/api/agent/queries/{taskId}` | Get an agent query task status |
| `POST` | `/api/agent/queries/{taskId}/result` | Complete a query task with structured public interest data |
| `GET` | `/api/activities/recent?userId=alice` | Get recent activities |
| `POST` | `/api/visits` | Record a visited link |
| `POST` | `/api/visits/import?userId=alice` | Import browser history CSV |

### POST /api/agent/queries

创建一个由本地 worker、Codex CLI 或 Agent Reach 执行的查询任务。Java 后端负责生成任务和 prompt，真正的平台读取动作由 worker 领取后执行。

Request:

```json
{
  "userId": "alice",
  "adapter": "agent-reach",
  "platform": "youtube",
  "intent": "read-video",
  "url": "https://www.youtube.com/watch?v=demo",
  "query": "AI agent local worker"
}
```

Response:

```json
{
  "taskId": "query-...",
  "userId": "alice",
  "adapter": "agent-reach",
  "platform": "youtube",
  "intent": "read-video",
  "url": "https://www.youtube.com/watch?v=demo",
  "query": "AI agent local worker",
  "prompt": "You are collecting public, non-private interest data...",
  "status": "PENDING"
}
```

### POST /api/agent/queries/claim-next

worker 领取最早的 `PENDING` 任务，任务状态变为 `RUNNING`。如果没有任务，返回 `NO_PENDING_AGENT_QUERY`。

### POST /api/agent/queries/{taskId}/result

worker 执行完成后回传结构化结果。成功时会复用 `/api/agent/tasks` 的规范化和入库逻辑。

Request:

```json
{
  "success": true,
  "ingest": true,
  "summary": "Worker returned public structured data.",
  "items": [
    {
      "platform": "youtube",
      "type": "WATCH",
      "externalId": "demo",
      "title": "YouTube AI Agent Local Worker",
      "url": "https://www.youtube.com/watch?v=demo",
      "author": "Demo Channel",
      "summary": "The video explains how a local worker can call an agent tool and return structured JSON.",
      "tags": ["youtube", "agent", "worker"]
    }
  ],
  "metadata": {
    "privacy": "public-interest-only"
  }
}
```

失败回传：

```json
{
  "success": false,
  "errorMessage": "Agent Reach failed to read subtitles."
}
```

### POST /api/agent/tasks

接收 Codex CLI、Agent Reach、Skill 或本地脚本返回的结构化兴趣数据。接口会把公开兴趣字段转换为 `BehaviorEventRequest`，再复用现有行为入库、画像学习和推荐刷新链路。

Request:

```json
{
  "userId": "alice",
  "taskId": "agent-bili-001",
  "adapter": "agent-reach",
  "intent": "read-video",
  "platform": "bilibili",
  "url": "https://www.bilibili.com/video/BV1demo",
  "ingest": true,
  "items": [
    {
      "platform": "bilibili",
      "type": "WATCH",
      "externalId": "BV1demo",
      "title": "B站推荐系统实践视频",
      "url": "https://www.bilibili.com/video/BV1demo",
      "author": "示例 UP 主",
      "summary": "视频主要讲推荐系统的数据采集、画像建模和内容召回流程。",
      "tags": ["bilibili", "推荐系统", "Java", "Agent"]
    }
  ],
  "metadata": {
    "privacy": "public-interest-only"
  }
}
```

Response:

```json
{
  "taskId": "agent-bili-001",
  "adapter": "agent-reach",
  "status": "INGESTED",
  "received": 1,
  "imported": 1,
  "skipped": 0,
  "events": [],
  "activities": [],
  "metadata": {
    "privacy": "public-interest-only"
  }
}
```

`ingest=false` 时只返回规范化后的事件，不写入数据库。推荐只提交公开标题、链接、作者、摘要、标签等非隐私字段。

### POST /api/datasources/events

统一采集事件入口，适合小红书、B站、YouTube、微信、飞书机器人或本地客户端提交数据。

Request:

```json
{
  "userId": "alice",
  "platform": "xiaohongshu",
  "type": "FAVORITE",
  "externalId": "note-id",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "author": "作者名",
  "summary": "用户收藏的小红书效率笔记",
  "text": "补充备注",
  "occurredAt": "2026-06-04T09:00:00",
  "tags": ["小红书", "AI", "效率"]
}
```

Response:

```json
{
  "id": 1,
  "type": "FAVORITE",
  "platform": "xiaohongshu",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "tags": ["xiaohongshu", "小红书", "AI", "效率"]
}
```

### POST /api/datasources/events/batch

Request:

```json
{
  "events": [
    {
      "userId": "alice",
      "platform": "bilibili",
      "type": "WATCH",
      "title": "B站 Spring Boot 推荐系统视频",
      "url": "https://www.bilibili.com/video/BV1demo",
      "tags": ["B站", "Java", "推荐系统"]
    }
  ]
}
```

Response:

```json
{
  "imported": 1,
  "skipped": 0,
  "activities": []
}
```

### Xiaohongshu Data

小红书数据建议通过用户主动提交，不建议模拟登录或绕过平台限制抓取。提交时统一使用：

```json
{
  "userId": "alice",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "platform": "xiaohongshu",
  "tags": ["小红书", "AI", "效率", "笔记"]
}
```

可用接口：

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/visits` | Submit one Xiaohongshu note/search link |
| `POST` | `/api/visits/import?userId=alice` | Import `sample-xiaohongshu-visits.csv` |
| `POST` | `/api/search-terms` | Submit a Xiaohongshu search term |
| `POST` | `/api/activities` | Submit favorite/like/dislike behavior |

## Recommendations

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/recommendations/today?userId=alice` | Get recommendations; refresh automatically if expired |
| `POST` | `/api/recommendations/generate?userId=alice` | Generate recommendations without deleting existing items |
| `POST` | `/api/recommendations/refresh?userId=alice` | Replace today's recommendations |
| `POST` | `/api/recommendations/search` | Record a keyword and generate recommendations immediately |
| `GET` | `/api/recommendations/refresh-policy?userId=alice` | Explain refresh interval and expiration |
| `POST` | `/api/recommendations/{id}/feedback` | Submit recommendation feedback |

### POST /api/recommendations/search

Request:

```json
{
  "userId": "alice",
  "keyword": "AI 编程",
  "platform": "web-search",
  "refresh": true
}
```

Response:

```json
{
  "userId": "alice",
  "keyword": "AI 编程",
  "profile": {
    "userId": "alice",
    "interestTerms": []
  },
  "recommendations": [],
  "refreshPolicy": {
    "userId": "alice",
    "recentActivityCount": 3,
    "activityWindowHours": 24,
    "refreshHours": 6,
    "expired": false
  }
}
```

`refresh=true` 会先替换当天推荐，再返回最新结果；`refresh=false` 会按过期策略自动判断是否刷新。

## Mini Program

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/mini/users/{userId}/dashboard` | Profile and recommendations |
| `POST` | `/api/mini/users/{userId}/visits` | Upload a visit |
| `POST` | `/api/mini/users/{userId}/search-terms` | Upload a search term |
| `POST` | `/api/mini/users/{userId}/activities` | Upload a behavior |
| `POST` | `/api/mini/users/{userId}/recommendations/generate` | Generate recommendations |

## Integrations

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/integrations/feishu/push-recommendations` | Push current recommendations to Feishu bot |

## Migration

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/admin/migration/activities/export` | Export behavior backup |
| `POST` | `/api/admin/migration/activities/import` | Import behavior backup |
| `GET` | `/api/admin/migration/full/export` | Export behavior, content and recommendation backup |
| `POST` | `/api/admin/migration/full/import` | Import full backup |

## Local Pages

| URL | Description |
| --- | --- |
| `http://localhost:8088/` | Vue demo UI served by Nginx; `/api` and `/actuator` proxy to Spring Boot |
| `http://localhost:8080/` | The same Vue demo UI served directly by Spring Boot |
