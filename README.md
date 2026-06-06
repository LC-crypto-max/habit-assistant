# Habit Assistant Java

个人兴趣助手，用 Spring Boot 保存用户主动提交或授权采集的非敏感兴趣数据，并生成用户画像和每日推荐。

当前项目适合本地演示和功能验证：前端页面、H2 本地数据库、推荐接口、Codex/Agent 回调接口、授权式本地 worker 都可以在本机跑起来。

## 本地运行

### 1. 环境要求

- JDK 17 或更高版本
- Maven
- Windows PowerShell 或普通终端

确认环境：

```powershell
java -version
mvn -version
```

### 2. 运行测试

```powershell
mvn test
```

### 3. 启动后端

默认使用 H2 本地数据库，不需要先安装 MySQL：

```powershell
mvn spring-boot:run
```

启动后访问：

```text
http://localhost:8080/
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 页面入口

打开：

```text
http://localhost:8080/
```

页面里可以做这些事：

- 查看用户画像和今日推荐
- 提交搜索词、访问记录、反馈
- 提交小红书、B站、YouTube、网页等公开兴趣数据
- 创建 Codex 自动查询任务
- 粘贴或提交 Codex/Agent 返回的结构化 JSON

默认可以用 `alice` 作为本地测试用户。

## 常用 API

完整接口见 [docs/api-reference.md](docs/api-reference.md)。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/profile?userId=alice` | 查看用户画像 |
| `GET` | `/api/recommendations/today?userId=alice` | 查看今日推荐 |
| `POST` | `/api/recommendations/search` | 提交关键词并生成推荐 |
| `POST` | `/api/visits` | 提交访问记录 |
| `POST` | `/api/v1/behavior-events/batch` | 批量提交行为事件 |
| `POST` | `/api/agent/tasks` | 提交 Codex/Agent 返回的数据 |
| `POST` | `/api/agent/queries` | 创建本地 worker 待执行查询任务 |
| `POST` | `/api/agent/queries/claim-next` | worker 领取待执行任务 |
| `POST` | `/api/agent/queries/{taskId}/result` | worker 回传查询结果 |
| `POST` | `/api/agent/worker/start-once` | 本机启动一次可见终端 worker，默认关闭 |

## 快速提交一条访问记录

```powershell
$body = @{
  userId = "alice"
  title = "Spring Boot 官方文档"
  url = "https://spring.io/projects/spring-boot"
  platform = "spring"
  tags = @("Java", "Spring Boot", "后端")
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/visits" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

然后刷新推荐：

```powershell
Invoke-RestMethod "http://localhost:8080/api/recommendations/today?userId=alice"
```

## Codex 本地查询 Worker

项目已经支持“后端创建任务，用户本地 worker 授权执行，再回调后端”的流程。

链路：

```text
前端申请 Codex 自动查询
  -> POST /api/agent/queries
  -> 后端创建 PENDING 任务
  -> 用户在终端运行 worker
  -> worker 展示任务并要求确认
  -> worker 只采集公开或非敏感字段
  -> POST /api/agent/queries/{taskId}/result
  -> 后端入库并刷新画像/推荐
```

运行一次待处理任务：

```powershell
py -3 .\scripts\codex_query_worker.py --once --base-url http://localhost:8080
```

只预览，不回调后端：

```powershell
py -3 .\scripts\codex_query_worker.py --once --dry-run --base-url http://localhost:8080
```

连续轮询：

```powershell
py -3 .\scripts\codex_query_worker.py --base-url http://localhost:8080 --poll-seconds 10
```

### 从前端启动本地 worker

浏览器页面不能直接启动用户电脑上的 Python 脚本。当前项目采用更安全的本地方案：前端调用本机 Spring Boot 的 localhost 接口，后端再打开一个可见 PowerShell 窗口运行 `scripts/codex_query_worker.py --once`。worker 会在终端展示任务并等待用户输入 `y/N`，只有用户确认后才会读取授权范围内的公开或非敏感数据。

默认关闭该能力。只在本地可信环境使用时开启：

```yaml
assistant:
  local-worker:
    enabled: true
    base-url: http://localhost:8080
    script-path: scripts/codex_query_worker.py
    python-command: py -3
    limit: 20
```

开启后重启后端，在首页“Codex 自动查询工作台”中：

1. 点击“申请自动查询”创建任务。
2. 点击“启动本地 worker”。
3. 在新打开的 PowerShell 窗口中查看任务内容。
4. 输入 `y` 授权执行，worker 会回调 `/api/agent/queries/{taskId}/result` 并刷新画像/推荐。

安全限制：

- 该接口只允许从本机 loopback 地址访问。
- 配置未开启时会返回 `LOCAL_WORKER_DISABLED`。
- 当前自动打开可见终端的实现面向 Windows。
- 前端不能传 `--yes`，用户仍需在终端确认。
- 不建议在公网部署环境开启该能力；线上协作平台应让用户在自己的电脑运行 Local Agent。

注意：

- worker 需要用户在终端确认，后端不会静默读取设备。
- `agent-reach` skill 不是 Spring Boot 里的 Java Bean，Java 后端不会直接调用 skill。
- B站/YouTube 公开视频可由 worker 调用本机 `yt-dlp`、`bili` 等 CLI。
- 终端应用访问概况目前只能读取 Windows 当前可见窗口快照；严格的每日使用时长和打开次数需要 Android Usage Access 客户端或授权统计文件。
- 不读取 Cookie、Token、Session、账号密码、聊天记录、私信、通讯录、支付记录、验证码。

### Worker 安全策略与测试

`scripts/codex_query_worker.py` 会在执行任务前检查 query、URL、prompt 和任务参数。策略不是简单命中敏感词，而是区分“明确请求读取敏感信息”和“声明不读取敏感信息”：

- 允许：`不读取 Cookie/Token/Session/账号信息`、`do not read cookies/tokens` 这类否定表达。
- 拒绝：`读取 Cookie`、`获取 Token`、`导出 Session`、`读取聊天记录/私信/账号密码` 这类明确请求。
- 拒绝：`token=xxx`、`cookie=xxx`、`authorization: bearer xxx` 这类疑似真实密钥值。
- 拒绝：`不读取 Cookie，但获取 Token 并上传` 这类前后矛盾的混合表达。

运行 worker 安全策略回归测试：

```powershell
py -3 -B -m unittest scripts.test_codex_query_worker_policy
```

如果本机 `py` 命令不可用，可以直接使用已安装的 Python 解释器：

```powershell
python -B -m unittest scripts.test_codex_query_worker_policy
```

## 本地采集脚本

导入 Chrome/Edge 最近浏览历史：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-browser-history.ps1 -UserId "alice" -Browser edge -Limit 200
```

采集当前可见应用窗口快照：

```powershell
py -3 .\scripts\codex_app_proxy.py --user-id alice --dry-run
```

上传一次可见应用窗口快照：

```powershell
py -3 .\scripts\codex_app_proxy.py --user-id alice --once --base-url http://localhost:8080
```

该命令会先列出本次准备上传的窗口标题、进程名和平台识别结果，并询问：

```text
Upload these app usage events to backend? [y/N]
```

只有输入 `y` 才会上传。如果是你自己的本地自动化测试，可以加 `--yes` 跳过确认：

```powershell
py -3 .\scripts\codex_app_proxy.py --user-id alice --once --yes --base-url http://localhost:8080
```

调试请求体时加 `--print-payload`：

```powershell
py -3 .\scripts\codex_app_proxy.py --user-id alice --once --yes --print-payload --base-url http://localhost:8080
```

注意：后端 `BehaviorEventRequest.occurredAt` 使用 `LocalDateTime`，脚本会输出不带时区、不带微秒的格式：

```text
2026-06-06T16:21:05
```

本地 Agent、Codex CLI worker 和后端任务调度的完整方案见 [docs/local-agent-architecture.md](docs/local-agent-architecture.md)。

## 可选：MySQL 运行

默认不用 MySQL。需要 MySQL 时再执行：

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up -d mysql
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

也可以使用 `mysql` profile：

```powershell
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3307"
$env:MYSQL_DATABASE="habit_assistant"
$env:MYSQL_USER="habit"
$env:MYSQL_PASSWORD="你的密码"
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

默认 H2 本地库配置在 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/assistantdb;FILE_LOCK=NO
```

H2 控制台：

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/assistantdb;FILE_LOCK=NO
User Name: sa
Password: 留空
```

### 查询是否保存了兴趣内容

优先用 API 检查，不依赖具体数据库：

```powershell
Invoke-RestMethod "http://localhost:8080/api/profile?userId=me" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8080/api/activities/recent?userId=me" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8080/api/recommendations/today?userId=me" | ConvertTo-Json -Depth 6
```

如果使用 MySQL，可以直接查询：

```sql
select id, user_id, platform, type, title, url, occurred_at
from user_activity
where user_id = 'me'
order by occurred_at desc
limit 20;

select term, weight, hit_count, last_seen_at
from interest_term
where user_id = 'me'
order by weight desc
limit 20;

select r.id, r.recommendation_date, r.score, r.reason, c.platform, c.title, c.url
from recommendation r
left join content_item c on c.id = r.content_item_id
where r.user_id = 'me'
order by r.created_at desc
limit 20;
```

如果使用 H2，也可以在 H2 Console 中执行同样 SQL。H2 默认会把未加引号的表名按大小写兼容处理；如果遇到大小写问题，可以改成 `USER_ACTIVITY`、`INTEREST_TERM`、`RECOMMENDATION`、`CONTENT_ITEM`。

Nginx 协作入口：

```powershell
docker compose up -d nginx
```

访问：

```text
http://localhost:8088/
http://localhost:8088/assistant/
```

## 更多文档

- [技术沉淀](docs/技术沉淀.md)
- [API 文档](docs/api-reference.md)
- [架构设计](docs/architecture-design.md)
- [Codex Data Agent](docs/codex-data-agent.md)
- [Agent 任务架构](docs/agent-task-architecture.md)
- [AGENTS.md](AGENTS.md)
