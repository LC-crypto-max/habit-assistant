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
- 小程序/飞书扩展：提供微信小程序风格 API 和飞书机器人推送入口。
- 数据迁移：支持 H2 行为数据和完整推荐数据迁移到 MySQL。

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
