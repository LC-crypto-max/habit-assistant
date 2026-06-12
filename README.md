# Habit Assistant Java

个人兴趣助手，用 Spring Boot 保存用户授权范围内的非敏感行为事件，生成兴趣画像和每日推荐。项目重点是把本机公开 URL 访问记录、Agent Reach 公开内容读取、Codex CLI 兴趣分析和后端行为入库串成一条可追踪链路。

## 解决什么问题

很多兴趣推荐系统要么依赖平台私有数据，要么需要服务端直接读取用户设备数据。本项目采用本地优先设计：

1. 本地 worker 只在用户显式启动后读取授权范围内的 URL 或模拟数据。
2. Agent Reach 只读取公开页面内容，不读取 Cookie、Token、Session 或账号数据。
3. Codex CLI 基于公开内容摘要分析兴趣标签、内容类型和推荐线索。
4. Spring Boot 接收标准 `behavior_event`，保存到 H2/MySQL，重建画像并生成推荐。

## 安全边界

项目禁止读取或保存：

- Cookie、Token、Session、Authorization Header
- 账号密码、验证码
- 微信聊天数据库、聊天记录、私信
- 通讯录、支付记录
- 平台私有接口或受保护内容

后端采集链路遵守 `DataPolicyChecker` 和 `PrivacySanitizer` 思路；worker 只返回公开、非敏感、可解释的兴趣信号。

## 标准 Behavior Event

worker 最终 POST 到 `/api/v1/behavior-events/batch` 的事件结构：

```json
{
  "userId": "me",
  "eventType": "VISIT",
  "platform": "bilibili",
  "source": "codex-cli-analysis",
  "url": "https://www.bilibili.com/video/BV1demo",
  "title": "Spring Boot Redis video",
  "summary": "公开内容摘要",
  "tags": ["bilibili", "Java后端"],
  "contentType": "video",
  "interestCategory": "backend",
  "confidence": "HIGH",
  "dataLevel": "PAGE_VISIBLE_CONTENT",
  "detectionReason": "public_url_enrichment",
  "rawEvidence": {
    "domain": "bilibili.com",
    "adapter": "agent-reach",
    "adapterMode": "mock"
  },
  "occurredAt": "2026-06-12T10:00:00"
}
```

兼容说明：后端同时接受 `type`/`eventType`、`contentCategory`/`interestCategory`；`tags` 使用 `List<String>`；`rawEvidence` 保存为 JSON 文本；时间字段使用 ISO-8601。

## 启动后端

```powershell
mvn spring-boot:run
```

默认使用本地 H2 数据库。启动后访问：

```text
http://localhost:8080/
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 启动 Worker

创建任务后，本地 worker 可以领取任务、读取授权 URL、调用 Agent Reach mock、调用 Codex CLI，并把行为事件写回后端：

```powershell
py -3 scripts/codex_query_worker.py --once --yes --use-codex-cli --direct-behavior-batch --verbose
```

常用参数：

- `--use-codex-cli`：启用 Codex CLI 兴趣分析。
- `--direct-behavior-batch`：直接 POST 到 `/api/v1/behavior-events/batch`。
- `--verbose`：打印当前任务、Agent Reach 命令和输出、Codex CLI 输入和输出、behavior_event JSON、后端 POST URL 和响应。
- `--dry-run`：只打印结果，不写后端。

## Agent Reach 的作用

`scripts/agent_reach_adapter.py` 提供统一入口：

```python
enrich_public_url(item: dict) -> dict
```

第一阶段使用 mock/dry-run，打印未来真实调用的命令：

```text
[AgentReach] 即将执行命令: agent-reach read --url "<url>" --platform "<platform>" --dry-run
[AgentReach] 返回结果: {...}
```

路由：

- `bilibili` -> `read_bilibili`
- `youtube` -> `read_youtube`
- `xiaohongshu` -> `read_xiaohongshu_public`
- `github` -> `read_github`
- `web` -> `read_web_page`

## Codex CLI 的作用

Codex CLI 不读取本机隐私数据，也不主动联网。worker 把 Agent Reach 返回的公开内容摘要作为 `raw_items` 输入给 Codex CLI，要求它只基于这些输入生成结构化兴趣字段：

- `tags`
- `contentType`
- `interestCategory`
- `summary`
- `recommendationHints`
- `confidence`
- `dataLevel`

如果 Codex CLI 不存在、超时、输出非 JSON、包含敏感字段或编造 URL，worker 会回退到原始公开 URL enrichment 结果。

## 测试

```powershell
mvn test
py -3 -B -m unittest scripts.test_agent_reach_adapter scripts.test_codex_query_worker_policy scripts.test_import_browser_history
```

## 关键文档

- API: [docs/api-reference.md](docs/api-reference.md)
- Worker 架构: [docs/agent-task-architecture.md](docs/agent-task-architecture.md)
- 技术沉淀: [docs/技术沉淀.md](docs/技术沉淀.md)
