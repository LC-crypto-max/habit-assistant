# Habit Assistant Java

个人兴趣助手，用 Spring Boot 保存用户授权范围内的非敏感行为事件，生成兴趣画像和每日推荐。当前版本的重点不是“多抓数据”，而是把公开 URL、平台适配、本地 worker、Agent Reach 和 Codex/LLM 分析串成一条可解释、可追踪、可回退的兴趣理解链路。

## 项目解决什么问题

很多推荐系统只能看到平台内部数据，或者需要服务端直接读取用户设备数据。本项目采用本地优先设计：

1. 用户在本机显式启动 worker。
2. worker 只读取授权范围内的公开 URL、标题、访问时间、访问次数或模拟数据。
3. Agent Reach/yt-dlp 只读取公开页面或公开视频元数据。
4. Codex/LLM 只分析 worker 提供的公开上下文，不读取本机文件、不主动访问外部网络。
5. Spring Boot 只接收标准化 `behavior_event`，负责入库、画像和推荐。

这样可以把“采集边界”和“理解能力”分开：敏感数据不出边界，LLM 只用来把公开内容理解成更有价值的兴趣信号。

## LLM 的作用

LLM 在本项目里不是采集器，而是兴趣分析器。它的价值主要体现在：

- 从视频标题、简介、作者、公开 URL 中理解内容语义，而不是只记录 `VISIT` 或 `WATCH`。
- 生成内容标签，例如 `spring boot`、`redis`、`backend`，避免把 `youtube`、`public-url`、`agent-reach` 这类系统标签误当成兴趣。
- 生成 3-5 行短摘要，帮助后端画像和推荐解释“用户为什么可能对这个主题感兴趣”。
- 判断 `interestCategory`，例如 `backend`、`AI`、`education`、`entertainment`、`other`。
- 输出 `confidence`，让画像构建时区分页面可见内容、浏览器历史和本地窗口信号的可信度。

当前 YouTube 链路已经升级为：

```text
公开 YouTube URL/videoId
  -> yt-dlp/公开元数据读取
  -> 构造 public context: title, description, author, url, videoId
  -> Codex/LLM JSON-only 分析
  -> 覆盖 placeholder tags/summary/interestCategory/confidence
  -> POST /api/v1/behavior-events/batch
```

如果 Codex CLI 不存在、超时、输出非 JSON、包含敏感字段或编造 URL，worker 会回退到元数据 enrichment 结果。

当前 worker 还会为每个满足条件的访问 URL 事件自动触发实时 LLM 分析。触发条件已经收窄为：事件必须有非空 `url`，并且 `eventType` 是 `VISIT`、`WATCH` 或 `FAVORITE`。`APP_USAGE`、无 URL 的 `SEARCH` 和纯窗口快照不会进入 LLM，避免把本地 App 使用信号误当作内容理解任务。分析异步运行，不阻塞后端入库；CLI 会在每个事件分析完成时立即打印：

```text
=====================================
[LLM ANALYSIS RESULT]
Platform: youtube
URL: https://www.youtube.com/watch?v=yt123456
Summary: ...
Tags: spring boot, redis, backend
Category: backend
Confidence: HIGH
=====================================
```

同时输出结构化日志：`llm_input_log`、`llm_output_log`、`latency_ms`、`token_usage`。如果 LLM 失败，事件仍会继续入库，并在 metadata 中标记 `llm_status=FAILED` 或保持 `PENDING` 等待异步结果。

### 统一 LLM Gateway

LLM 调用统一收口到 `scripts/llm_gateway.py` 和 `scripts/worker_llm_gateway.py`：

- `WorkerLLMGateway` 是 worker 面向业务流程的入口，`codex_query_worker.py` 不直接耦合 analyzer 内部实现。
- `LLMGateway` 负责解析 Codex/LLM 命令、执行子进程、解析严格 JSON、统计 `latency_ms/token_usage` 并做错误脱敏。
- Windows 下会先用 `shutil.which()` 找到真实可执行文件；如果解析到 `codex.cmd` 或 `.bat`，会通过 shell 命令行执行，避免 `WinError 2`。
- LLM 成功返回后会覆盖事件顶层 `summary`、`tags`、`interestCategory`、`intent`、`confidence`，不再只写入 `rawMetadata.llm_result`。

## 安全边界

禁止读取或保存：

- Cookie、Token、Session、Authorization Header
- 账号密码、验证码
- 微信聊天数据库、聊天记录、私信
- 通讯录、支付记录
- 平台私有接口或受保护内容

允许处理：

- 用户主动提供的公开 URL
- 浏览器历史中的 URL、标题、访问时间、访问次数
- App 名称、包名、使用时长、打开次数
- 用户授权目录下的本地笔记
- 公开页面的 title、description、keywords
- 公开 YouTube 视频的 title、description、channel/author、url、videoId

## 标准 Behavior Event

worker 最终写入 `/api/v1/behavior-events/batch` 的事件结构：

```json
{
  "userId": "me",
  "platform": "youtube",
  "eventType": "WATCH",
  "source": "codex-cli-analysis",
  "url": "https://www.youtube.com/watch?v=yt123456",
  "externalId": "yt123456",
  "title": "Spring Boot Redis caching tutorial",
  "author": "Backend Channel",
  "contentSnippet": "This video explains Spring Boot Redis caching for backend APIs.",
  "summary": "This video explains Spring Boot Redis caching for backend APIs.",
  "tags": ["spring boot", "redis", "backend"],
  "contentType": "video",
  "interestCategory": "backend",
  "confidence": "HIGH",
  "dataLevel": "PAGE_VISIBLE_CONTENT",
  "detectionReason": "public_url_enrichment",
  "rawMetadata": {
    "domain": "youtube.com",
    "externalId": "yt123456",
    "contentType": "video",
    "interestCategory": "backend"
  },
  "occurredAt": "2026-06-12T10:00:00"
}
```

支持平台：`youtube`、`bilibili`、`baidu`、`xiaohongshu`、`web`。后端兼容 `type/eventType`、`summary/contentSnippet`、`rawEvidence/rawMetadata`、`contentCategory/interestCategory`。

## 后端能力

- `/api/v1/behavior-events/batch` 接收批量行为事件。
- DTO 支持 ISO-8601 时间、数组 tags、对象 rawMetadata。
- adapter 层负责多平台归一化：
  - `YouTubeAdapter`
  - `BilibiliAdapter`
  - `BaiduSearchAdapter`
  - `XiaohongshuAdapter`
  - `GenericWebAdapter`
- `BehaviorEventValidator` 在入库前拒绝 unknown platform、缺失 URL/externalId、缺失 eventType、platform/url mismatch。
- 无效事件写入 `INVALID_EVENT_LOG`，日志不打印敏感原文。

## 启动后端

```powershell
mvn spring-boot:run
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 启动 Worker

创建任务后，本地 worker 可以领取任务、读取授权 URL、调用公开元数据读取、调用 Codex CLI，并把事件写回后端：

```powershell
py -3 scripts/codex_query_worker.py --once --yes --use-codex-cli --direct-behavior-batch --verbose
```

常用参数：

- `--use-codex-cli`：兼容旧参数；当前实时 Codex/LLM 分析会自动触发。
- `--direct-behavior-batch`：直接 POST 到 `/api/v1/behavior-events/batch`。
- `--verbose`：打印任务、Agent Reach 命令、Codex CLI 输入输出、behavior_event JSON、后端响应。
- `--dry-run`：只打印结果，不写后端。

## 项目结构分析

```text
src/main/java/com/example/assistant
  controller/              REST API 入口
  dto/                     请求/响应 DTO
  model/                   JPA 实体和枚举
  repo/                    Spring Data Repository
  service/                 核心业务服务
  service/behavior/        behavior_event 入库、发布、校验
  service/behavior/adapter 多平台事件归一化 adapter
  service/agent/           agent task/query 调度
  codexagent/              Codex Data Agent 模块

scripts/
  codex_query_worker.py    本地 worker 主流程
  agent_reach_adapter.py   公开 URL enrichment 适配层
  worker_llm_gateway.py    worker 侧统一 LLM 入口
  llm_gateway.py           Codex/LLM 子进程执行、JSON 解析和 WinError 2 兼容层
  codex_analyzer.py        异步 LLM 分析、事件筛选和 CLI 实时输出
  test_*.py                worker 与策略测试

docs/
  技术沉淀.md              本地 worker/LLM/安全边界设计沉淀
  api-reference.md         API 说明
  agent-task-architecture.md Agent 任务架构
```

当前结构的优点是边界清晰：Spring Boot 不直接读本机数据，worker 不直接写数据库，LLM 不做采集，只做结构化理解。

当前需要持续优化的点：

- `codex_query_worker.py` 已承担采集、清洗、LLM 调用、POST、CLI 参数解析，后续应拆成 `collectors/`、`analysis/`、`transport/`、`policy/`。
- Codex prompt 目前在代码中，后续可提取到模板文件，便于版本化和 A/B 测试。
- `rawMetadata` 白名单已经可控，后续可增加 schema version。
- Agent Reach 当前 mock/dry-run 为主，替换真实读取时应保持相同输出契约。

## 测试

```powershell
mvn test
py -3 -B -m unittest scripts.test_agent_reach_adapter scripts.test_codex_query_worker_policy scripts.test_import_browser_history
```

## 关键文档

- API: [docs/api-reference.md](docs/api-reference.md)
- Worker 架构: [docs/agent-task-architecture.md](docs/agent-task-architecture.md)
- 技术沉淀: [docs/技术沉淀.md](docs/技术沉淀.md)
