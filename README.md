# Habit Assistant Java

个人兴趣助手，用 Spring Boot 保存用户授权范围内的非敏感行为事件，生成兴趣画像和每日推荐。当前版本把公开 URL、平台适配、本地 worker、Agent Reach 与 TRAE/Codex CLI 串成一条可解释、可追踪、可回退的兴趣理解链路。

## 当前完成状态

面向演示的 MVP 已完成并通过本机验收：页面可提交本人授权的小红书公开笔记 URL，
显式允许本次复用浏览器登录会话，启动本地 worker，经 OpenCLI/Agent Reach 和 TRAE CLI
分析后写入后端，并在同一页面展示访问证据、AI 摘要、兴趣画像和今日推荐。
Codex CLI 保留为可选备援 Provider；页面可分别选择语义分析 Provider 与内容分析渠道。

当前不能宣称“读取整个小红书账号的全部浏览历史”。系统只处理用户主动提交的公开链接、
导入的浏览器历史和允许范围内的本地数据；不读取推荐 feed、收藏、通知、私信或账号凭据。
对于包含 `xsec_token` 的完整分享链接，签名 URL 只在浏览器内存中用于打开本人授权的页面；
后端、TRAE/Codex、数据库和日志仅接收不含签名参数的真实公开规范 URL 与 OpenCLI 提取的页面可见正文。

已验证结果：

- Java：41 个测试全部通过；
- Python：63 个测试全部通过；
- Vue 3/Vite 生产构建成功，旧静态 HTML 入口已删除；
- 可执行 JAR 启动成功，`/actuator/health` 返回 `UP`；
- 首页、`/api/v1/demo-system/status` 与 `/api/v1/demo-snapshots/xiaohongshu` 返回 200；
- 桌面和 375px 移动端通过浏览器验收，无横向溢出；
- `trae-cli 0.1.0` 的命令与配置加载路径已验证；真实模型分析需要有效 Provider API Key；
- 当前机器已检测到 `xhs`，但未检测到 `opencli`；小红书主演示必须先补齐 OpenCLI 命令和浏览器扩展。`bili`、`yt-dlp` 与 `codex-cli` 可作为其他渠道工具。

## 项目解决什么问题

很多推荐系统只能看到平台内部数据，或者需要服务端直接读取用户设备数据。本项目采用本地优先设计：

1. 用户在本机显式启动 worker。
2. worker 只读取授权范围内的公开 URL、标题、访问时间、访问次数或模拟数据。
3. Agent Reach channel tools只读取用户授权的公开页面或公开视频元数据；登录态只允许 OpenCLI 在本机复用，不导出凭据。
4. TRAE/Codex 只分析 worker 提供的公开上下文，并在隔离临时目录中运行。
5. Spring Boot 只接收标准化 `behavior_event`，负责入库、画像和推荐。

这样可以把“采集边界”和“理解能力”分开：敏感数据不出边界，LLM 只用来把公开内容理解成更有价值的兴趣信号。

## LLM 的作用

语义分析现在支持 `codex` 和 `trae` 两种 CLI Provider。两者复用同一条 Agent Reach、PolicyGate、PrivacySanitizer、行为入库、画像和推荐链路；TRAE 只在临时隔离目录中接收经过脱敏的公开页面上下文，不会获得项目目录、Cookie、Token 或 Session。

页面可直接选择 **TRAE CLI（推荐演示）** 或 **Codex CLI（备选）**。命令行也可以显式选择：

```powershell
py -3 scripts/codex_query_worker.py --once --analysis-provider trae `
  --analysis-channel agent-reach --trae-command trae-cli --trae-max-steps 8 --agent-reach-mode live `
  --allow-authenticated-browser --yes --verbose
```

TRAE CLI 调用形式为 `trae-cli run --file <临时任务说明> --working-dir <临时目录> --trajectory-file <临时轨迹> --console-type simple`。由于 `trae-cli 0.1.0` 内置的是软件工程 Agent 提示，Gateway 会把脱敏公开上下文放入一次性分析工作区，并要求 TRAE 将结果写入固定的 `analysis-result.json`；系统优先读取并校验该文件，不再依赖包含步骤面板的控制台文本。固定文件、轨迹和控制台都没有有效 JSON 时，任务失败且不会把占位摘要写入数据库。

运行前需要确保 `C:\Users\Lenovo\trae-agent\trae_config.yaml` 中所选 Provider 使用真实有效的 API Key。新版 TRAE 模型配置还需要 `top_p`、`top_k`、`parallel_tool_calls` 和 `max_retries`；未配置 Lakeview 时应设置 `enable_lakeview: false`。

### TRAE 分析渠道

页面和 Worker 将模型与内容渠道分开配置：

- `analysis-provider=trae|codex`：选择负责语义理解的 CLI。
- `analysis-channel=agent-reach`：由本机 Agent Reach 按平台路由读取公开内容，再交给所选 CLI 分析；这是小红书主演示渠道。
- `analysis-channel=public-metadata`：只使用脱敏 URL、标题等公开元数据，作为渠道不可用时的安全降级。

当前 Agent Reach 适配器已经覆盖小红书、B站、YouTube、GitHub和通用网页等路由。小红书现场演示使用 OpenCLI 读取用户当前明确授权的可见公开标签页；项目不会把 Cookie、Token、Session 或 `xsec_token` 写入任务、TRAE 提示词、日志和数据库。教程中直接持久化 Cookie 的方式不属于本项目支持范围。

LLM 在本项目里不是采集器，而是兴趣分析器。它的价值主要体现在：

- 从视频标题、简介、作者、公开 URL 中理解内容语义，而不是只记录 `VISIT` 或 `WATCH`。
- 生成内容标签，例如 `spring boot`、`redis`、`backend`，避免把 `youtube`、`public-url`、`agent-reach` 这类系统标签误当成兴趣。
- 生成 3-5 行短摘要，帮助后端画像和推荐解释“用户为什么可能对这个主题感兴趣”。
- 判断 `interestCategory`，例如 `backend`、`AI`、`education`、`entertainment`、`other`。
- 输出 `confidence`，让画像构建时区分页面可见内容、浏览器历史和本地窗口信号的可信度。

当前公开视频链路已经升级为：

```text
公开 YouTube URL/videoId
  -> yt-dlp/公开元数据读取
  -> 构造 public context: title, description, author, url, videoId
  -> Codex/LLM JSON-only 分析
  -> 覆盖 placeholder tags/summary/interestCategory/confidence
  -> POST /api/v1/behavior-events/batch
```

小红书公开笔记由 OpenCLI 绑定刚打开且 noteId 一致的 Chrome 标签页读取；失败时明确提示用户
重新打开，而不再自动搜索并制造额外标签页；Bilibili 视频通过 `bili`，普通网页通过 Jina Reader/curl；
只有真实工具成功返回页面可见内容时，事件才会提升为 `HIGH / PAGE_VISIBLE_CONTENT`。

如果小红书 Agent Reach 未取得真实页面内容，或所选 TRAE/Codex CLI 不存在、超时、输出非 JSON，worker 会将任务标记失败，不再把 URL 占位摘要当成成功结果写入数据库。

当前 worker 还会为每个满足条件的访问 URL 事件自动触发 LLM 分析。触发条件已经收窄为：事件必须有非空 `url`，并且 `eventType` 是 `VISIT`、`WATCH` 或 `FAVORITE`。`APP_USAGE`、无 URL 的 `SEARCH` 和纯窗口快照不会进入 LLM，避免把本地 App 使用信号误当作内容理解任务。多个事件在线程池中并行分析，但 worker 会在写入后端前等待全部结果，确保画像拿到最终语义标签；CLI 会在每个事件分析完成时立即打印：

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

同时输出结构化日志：`llm_input_log`、`llm_output_log`、`latency_ms`、`token_usage`。如果必需的 LLM 分析失败，任务会停止入库并返回不含凭据的诊断信息。

### 统一 LLM Gateway

LLM 调用统一收口到 `scripts/llm_gateway.py` 和 `scripts/worker_llm_gateway.py`：

- `WorkerLLMGateway` 是 worker 面向业务流程的入口，`codex_query_worker.py` 不直接耦合 analyzer 内部实现。
- `LLMGateway` 负责解析 Codex/LLM 命令、执行子进程、解析严格 JSON、统计 `latency_ms/token_usage` 并做错误脱敏。
- Windows 下会先用 `shutil.which()` 找到真实可执行文件；如果解析到 `codex.cmd` 或 `.bat`，会用 `cmd.exe /d /s /c` 包装参数并继续保持 `shell=False`，避免 `WinError 2` 和不必要的 shell 扩展。
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
- `/api/v1/demo-snapshots/xiaohongshu` 聚合访问证据、Agent Reach、TRAE/Codex、画像和推荐状态，供现场演示一次读取。
- `/api/agent/worker/start-once` 仅允许本机 loopback 调用；Vue 页面勾选授权后会传递本次确认，并在可见 PowerShell 中展示阶段日志。
- `/api/v1/demo-system/status` 返回当前实际数据库类型、版本、profile 与连通状态，页面不会把 H2 误标成 MySQL。
- DTO 支持 ISO-8601 时间、数组 tags、对象 rawMetadata。
- adapter 层负责多平台归一化：
  - `YouTubeAdapter`
  - `BilibiliAdapter`
  - `BaiduSearchAdapter`
  - `XiaohongshuAdapter`
  - `GenericWebAdapter`
- `BehaviorEventValidator` 在入库前拒绝 unknown platform、缺失 URL/externalId、缺失 eventType、platform/url mismatch。
- 无效事件写入 `INVALID_EVENT_LOG`，日志不打印敏感原文。

## 配置本地 MySQL

默认 profile 已切换为 `mysql`。你可以直接编辑已创建、且被 Git 忽略的
[`config/application-mysql-local.yml`](config/application-mysql-local.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/habit_assistant?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_mysql_user
    password: your_mysql_password
```

把 `username/password` 改为你本机 MySQL 账号和密码即可。不要把该文件提交到 Git，
也不要把密码发到聊天或日志。应用启动时会自动执行
[`db/mysql/schema.sql`](src/main/resources/db/mysql/schema.sql)，创建演示表并修复旧版短字段。

如果不想把密码写在本地文件中，也可以运行 `./scripts/start-demo-mysql.ps1`，脚本会在
当前 PowerShell 进程中安全提示输入密码，不落盘、不打印密码。

## 构建 Vue 前端

仓库已包含构建后的 Vue 产物；只有修改 `frontend/` 后才需要重新构建：

```powershell
Set-Location .\frontend
npm.cmd install --cache ..\.npm-cache
npm.cmd run build
Set-Location ..
```

Vite 会把生产产物写入 `src/main/resources/static`，Spring Boot 与 Nginx 共用同一份页面。

## 启动后端

```powershell
$demoJdk = Get-ChildItem -Directory .\.local-tools\jdk17 | Select-Object -First 1
$env:JAVA_HOME = $demoJdk.FullName
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
mvn package -DskipTests
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\habit-assistant-java-0.0.1-SNAPSHOT.jar
```

启动成功后，页面右上角应显示“`MySQL 演示服务在线`”。也可以直接检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/demo-system/status
```

如暂时没有 MySQL，只用于开发回归时可显式运行 `--spring.profiles.active=h2`；演示请使用默认 MySQL。

项目要求 JDK 17+。若机器默认 Maven 仍使用 Java 8，可按
[小红书演示指南](docs/xiaohongshu-demo-guide.md) 切换到项目内 `.local-tools/jdk17`。

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 启动 Worker

创建任务后，本地 worker 可以领取任务、读取授权 URL、调用 Agent Reach，并通过 TRAE 或 Codex CLI 分析后把事件写回后端：

```powershell
$demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $demoPython scripts/codex_query_worker.py --once --direct-behavior-batch `
  --analysis-provider trae --analysis-channel agent-reach `
  --agent-reach-mode live --allow-authenticated-browser --verbose
```

常用参数：

- `--agent-reach-mode auto|live|off`：控制真实 URL enrichment；默认 `auto`。
- `--analysis-provider trae|codex`：选择语义分析 CLI；小红书主演示使用 `trae`。
- `--analysis-channel agent-reach|public-metadata`：选择内容证据渠道；主演示使用 `agent-reach`。
- `--allow-authenticated-browser`：显式允许 OpenCLI 复用现有浏览器登录来读取已提供的小红书公开笔记 URL，不会导出或保存 Cookie/Token。
- `--direct-behavior-batch`：直接 POST 到 `/api/v1/behavior-events/batch`。
- `--verbose`：打印任务、脱敏后的 Agent Reach route/status、TRAE/Codex 分析结果、behavior_event JSON 和后端响应。
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
  trae-xiaohongshu-demo-guide.md TRAE CLI 小红书主演示手册
  技术沉淀.md              本地 worker/LLM/安全边界设计沉淀
  api-reference.md         API 说明
  agent-task-architecture.md Agent 任务架构
```

当前结构的优点是边界清晰：Spring Boot 不直接读本机数据，worker 不直接写数据库，LLM 不做采集，只做结构化理解。

当前需要持续优化的点：

- `codex_query_worker.py` 已承担采集、清洗、LLM 调用、POST、CLI 参数解析，后续应拆成 `collectors/`、`analysis/`、`transport/`、`policy/`。
- Codex prompt 目前在代码中，后续可提取到模板文件，便于版本化和 A/B 测试。
- `rawMetadata` 白名单已经可控，后续可增加 schema version。
- Agent Reach 已接入真实 channel 命令；没有可用后端时保留原始浏览历史置信度并记录降级状态。

## 测试

```powershell
$demoJdk = Get-ChildItem -Directory .\.local-tools\jdk17 | Select-Object -First 1
$env:JAVA_HOME = $demoJdk.FullName
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
mvn test

$demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $demoPython -m unittest discover -s scripts -p 'test_*.py'
```

## 关键文档

- TRAE 小红书主演示: [docs/trae-xiaohongshu-demo-guide.md](docs/trae-xiaohongshu-demo-guide.md)
- 通用小红书演示: [docs/xiaohongshu-demo-guide.md](docs/xiaohongshu-demo-guide.md)
- 当前目标完成度: [docs/completion-assessment.md](docs/completion-assessment.md)
- API: [docs/api-reference.md](docs/api-reference.md)
- Worker 架构: [docs/agent-task-architecture.md](docs/agent-task-architecture.md)
- 技术沉淀: [docs/技术沉淀.md](docs/技术沉淀.md)
