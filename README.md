# Habit Assistant Java

个人兴趣画像与内容推荐助手。项目支持多用户在线提交搜索词、浏览记录、观看行为和推荐反馈，后端按 `userId` 形成用户画像，并生成可点击的内容推荐。当前版本适合本地验证、课程/面试展示，也可以扩展成 5~10 人的小型协作平台。

## 技术栈

- Java 17
- Spring Boot 3.3.2
- Spring MVC + Bean Validation
- Spring Data JPA
- H2 本地开发库
- MySQL 8 线上/协作运行库
- Maven
- Docker Compose + Nginx
- 原生 HTML/CSS/JavaScript 前端

## 项目结构

```text
habit-assistant-java
├─ src/main/java/com/example/assistant
│  ├─ controller        # REST API
│  ├─ service           # 画像、推荐、采集、迁移业务
│  ├─ repo              # Spring Data JPA Repository
│  ├─ model             # JPA 实体
│  ├─ dto               # 请求和响应对象
│  └─ config            # 配置与初始化
├─ src/main/resources
│  ├─ static/index.html # 完整画像与推荐后台
│  └─ application*.yml  # 本地、MySQL 配置
├─ web/index.html       # Nginx 简单协作提交页
├─ scripts              # 本地验证、浏览器历史、应用使用采集脚本
├─ docs                 # 技术沉淀、API 文档、修改报告
├─ nginx                # Nginx 反向代理配置
└─ docker-compose*.yml  # MySQL / Nginx 本地编排
```

## 核心功能

- 多用户画像：按 `userId` 隔离行为、兴趣词、推荐结果和反馈。
- 行为采集：支持访问记录、搜索词、观看/喜欢/收藏/不感兴趣等行为。
- 智能推荐：综合兴趣词、本次搜索词、内容新鲜度和显式反馈打分。
- 动态刷新：按近 24 小时行为量自动选择 3/6/12 小时推荐有效期。
- 前端后台：自动刷新推荐、关键词搜索推荐、推荐反馈、画像关键词展示。
- 本地采集脚本：可选导入 Chrome/Edge 历史，检测本机应用使用情况。
- 小红书数据源：支持用户主动提交小红书链接、CSV 导入、小程序/飞书入口扩展。
- 小程序/飞书扩展：提供微信小程序风格 API 和飞书机器人推送入口。
- 数据迁移：支持 H2 行为数据和完整推荐数据迁移到 MySQL。

完整架构设计见 [docs/architecture-design.md](docs/architecture-design.md)。

## 本地启动

默认使用 H2 文件库，适合快速验证：

```powershell
mvn test
mvn spring-boot:run
```

访问地址：

```text
http://localhost:8080/
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## MySQL 与 Docker

复制环境变量模板，并填写自己的本地密码：

```powershell
Copy-Item .env.example .env
notepad .env
```

启动 MySQL：

```powershell
docker compose up -d mysql
```

启动 Spring Boot MySQL profile：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

启动 Nginx 协作入口：

```powershell
docker compose up -d nginx
```

访问地址：

```text
http://localhost:8088/            # 简单协作提交页
http://localhost:8088/assistant/  # 完整画像与推荐后台
http://localhost:8080/            # 直接访问 Spring Boot 后台
```

MySQL 连接变量：

```text
MYSQL_HOST=localhost
MYSQL_PORT=3307
MYSQL_DATABASE=habit_assistant
MYSQL_USER=habit
MYSQL_PASSWORD=你自己的密码
```

`.env` 不应提交到 GitHub，仓库只保留 `.env.example`。

## API 示例

完整接口见 [docs/api-reference.md](docs/api-reference.md)。

### 提交访问记录

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

### 搜索关键词并生成推荐

```powershell
$body = @{
  userId = "alice"
  keyword = "AI 编程"
  platform = "web-search"
  refresh = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/recommendations/search" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

### 常用接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/profile?userId=alice` | 查询用户画像 |
| `GET` | `/api/profile/users` | 查询用户列表 |
| `POST` | `/api/activities` | 提交通用行为 |
| `POST` | `/api/datasources/events` | 提交单条平台采集事件 |
| `POST` | `/api/datasources/events/batch` | 批量提交小红书/B站/YouTube 等采集事件 |
| `POST` | `/api/search-terms` | 提交搜索词 |
| `POST` | `/api/visits` | 提交访问记录 |
| `POST` | `/api/visits/import?userId=alice` | 上传 CSV 浏览记录 |
| `GET` | `/api/recommendations/today?userId=alice` | 获取推荐，过期自动刷新 |
| `POST` | `/api/recommendations/search` | 关键词搜索并生成推荐 |
| `POST` | `/api/recommendations/refresh?userId=alice` | 强制刷新推荐 |
| `GET` | `/api/recommendations/refresh-policy?userId=alice` | 查看刷新策略 |
| `POST` | `/api/recommendations/{id}/feedback` | 提交推荐反馈 |

## 本地采集脚本

导入 Chrome/Edge 最近浏览历史：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-browser-history.ps1 -UserId "alice" -Browser edge -Limit 200
```

说明：该脚本依赖本机 `sqlite3` 命令，会复制浏览器历史库到 `data/browser-history-copy` 后读取最近 URL。

检测本机应用使用情况，例如哔哩哔哩客户端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collect-app-usage.ps1 -UserId "alice" -ProcessName "bilibili" -Platform "bilibili-app"
```

本项目不会静默读取本机数据，采集脚本需要用户主动执行。

### 小红书数据接入

小红书个人访问、收藏、推荐流通常没有通用个人开放 API。项目推荐采用合规路线：用户主动提交链接或 CSV，小程序/飞书机器人做提交入口，后续如拿到官方或授权数据源再实现专用 Collector。

本地导入示例：

```powershell
curl.exe -X POST "http://localhost:8080/api/visits/import?userId=alice" `
  -F "file=@sample-xiaohongshu-visits.csv"
```

生成小红书相关推荐：

```powershell
$body = @{
  userId = "alice"
  keyword = "小红书 AI 效率 笔记"
  platform = "xiaohongshu"
  refresh = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/recommendations/search" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

详细方案见 [docs/xiaohongshu-integration-plan.md](docs/xiaohongshu-integration-plan.md)。

### 多平台采集事件接口

小红书、B站、YouTube、微信等本地客户端或机器人采集结果，可以统一提交到：

```powershell
$body = @{
  events = @(
    @{
      userId = "alice"
      platform = "xiaohongshu"
      type = "FAVORITE"
      title = "小红书AI效率笔记收藏"
      url = "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记"
      summary = "用户收藏的小红书效率笔记"
      tags = @("小红书", "AI", "效率")
    },
    @{
      userId = "alice"
      platform = "bilibili"
      type = "WATCH"
      title = "B站 Spring Boot 推荐系统视频"
      url = "https://www.bilibili.com/video/BV1demo"
      tags = @("B站", "Java", "推荐系统")
    },
    @{
      userId = "alice"
      platform = "youtube"
      type = "WATCH"
      title = "YouTube AI Agent Tutorial"
      url = "https://www.youtube.com/watch?v=demo"
      tags = @("YouTube", "AI", "Agent")
    }
  )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/datasources/events/batch" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

## 测试与验证

优先使用不占用真实端口的测试：

```powershell
mvn -DskipTests compile
mvn test
```

这会使用 MockMvc + H2 memory 验证 Controller、Service 和 Repository，不需要启动 `localhost:8080`。

如需验证打包后的 jar，请使用后台脚本，不要前台运行 `java -jar`：

```powershell
mvn package
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -StartupTimeoutSeconds 45
```

## GitHub 上传准备

仓库已准备 `.gitignore`，默认忽略：

- `target/`
- `.idea/`、`.vscode/`
- `logs/`
- `data/`
- `.env`
- 本地数据库文件
- 前端构建产物

首次上传建议：

```powershell
git init
git status
git add .
git commit -m "init: add project with optimized frontend UI"
git remote add origin <你的GitHub仓库地址>
git branch -M main
git push -u origin main
```

如果远程仓库已存在：

```powershell
git remote -v
git remote set-url origin <你的GitHub仓库地址>
git push -u origin main
```

## 常见问题

### 页面访问 404

直接访问 Spring Boot 后台用：

```text
http://localhost:8080/
```

通过 Nginx 访问完整后台用：

```text
http://localhost:8088/assistant/
```

### Docker MySQL 启动失败

先确认 `.env` 已填写：

```powershell
Get-Content .env
docker compose config
docker compose up -d mysql
```

### PowerShell 中文乱码

请求时使用 UTF-8 body：

```powershell
-ContentType "application/json; charset=utf-8"
-Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

### Windows target 文件拒绝访问

通常是旧 Java 进程或 IDE 占用 `target/` 文件。先停止旧服务，再删除单个目标文件或重新编译。

## 后续优化方向

- 接入登录鉴权，避免长期依赖前端传入 `userId`。
- 为 `/api/admin/migration/**` 增加管理员权限保护。
- 加入 Redis 做推荐缓存和接口限流。
- 引入消息队列异步处理采集、画像更新和推荐生成。
- 内容量扩大后引入 Elasticsearch/OpenSearch 做候选召回。
- 引入向量召回或大模型重排，进一步提升推荐质量。
## Profile 每日画像接口

新增 `GET /api/profile/daily?userId=alice`，用于给 HTML 前端、微信小程序或飞书机器人渲染每日用户画像。

能力：

- 统计最近 7 天和 30 天行为数量。
- 聚合行为类型和平台分布。
- 从标题、正文、平台和标签中抽取兴趣标签并计算权重。
- 输出自然语言摘要，当前为规则生成 Mock 摘要，后续可替换为大模型摘要。
- 支持 Redis 缓存每日画像，默认关闭，本地无需 Redis。

调用示例：

```powershell
Invoke-RestMethod "http://localhost:8080/api/profile/daily?userId=alice&refresh=true"
```

返回结构示例：

```json
{
  "userId": "alice",
  "profileDate": "2026-06-04",
  "generatedAt": "2026-06-04T10:00:00",
  "summary": "用户 alice 最近 7 天产生 12 条行为，30 天累计 40 条。当前主要兴趣集中在 AI、Java、推荐系统。",
  "last7Days": {
    "days": 7,
    "activityCount": 12,
    "typeCounts": {"WATCH": 6, "SEARCH": 4, "FAVORITE": 2},
    "platformCounts": {"bilibili": 5, "xiaohongshu": 4, "wechat": 3},
    "tags": []
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

启用 Redis 每日画像缓存：

```yaml
assistant:
  profile:
    cache:
      redis:
        enabled: true
        ttl-hours: 24

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## Behavior 事件批量上传接口

新增统一行为入口 `POST /api/v1/behavior-events/batch`，用于接收小红书、微信、B站等本地客户端、微信小程序、飞书机器人或 Agent Reach 采集器输出的数据。

请求示例：

```powershell
$body = @{
  events = @(
    @{
      userId = "alice"
      platform = "xiaohongshu"
      source = "agent-reach"
      externalId = "xhs-note-001"
      type = "FAVORITE"
      title = "小红书 AI 工作流笔记"
      url = "https://www.xiaohongshu.com/explore/demo-note"
      summary = "用户收藏的小红书效率笔记"
      tags = @("小红书", "AI", "效率")
    },
    @{
      userId = "alice"
      platform = "wechat"
      source = "wechat-mini"
      externalId = "wechat-link-001"
      type = "VISIT"
      title = "微信公众号阅读清单"
      url = "https://mp.weixin.qq.com/s/demo"
      tags = @("微信", "阅读")
    },
    @{
      userId = "alice"
      platform = "bilibili"
      source = "agent-reach"
      externalId = "BV1demo"
      type = "WATCH"
      title = "B站 Spring Boot 推荐系统视频"
      url = "https://www.bilibili.com/video/BV1demo"
      tags = @("B站", "Java", "推荐系统")
    }
  )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/behavior-events/batch" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

返回字段：

| 字段 | 说明 |
| --- | --- |
| `imported` | 成功写入行为表的事件数量 |
| `skipped` | 空事件跳过数量 |
| `messagesPublished` | 成功发送到 RabbitMQ 的消息数量 |
| `activities` | 写库后的行为记录 |

默认情况下 RabbitMQ 发送关闭，便于本地直接运行。线上启用时增加配置：

```yaml
assistant:
  behavior:
    rabbitmq:
      enabled: true
      exchange: habit.behavior.events
      routing-key: behavior.events.created

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

使用 MySQL profile 运行时，该接口写入 MySQL；测试 profile 使用 H2 memory，不依赖真实 MySQL 或 RabbitMQ。
