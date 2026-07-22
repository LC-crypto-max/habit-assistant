# 小红书 AI 兴趣分析演示指南

## 演示边界

本次演示证明的是一条“用户主动提交本人访问的公开笔记”的最小授权链路：

```text
Vue 页面粘贴小红书完整分享文案
  -> 签名 URL 仅在浏览器内打开，后端只接收规范公开 URL
  -> 用户勾选单次浏览器会话授权
  -> 本地 PowerShell Worker 精确领取该任务
  -> OpenCLI / Agent Reach 读取页面可见公开内容
  -> Codex CLI 生成摘要、标签、类别和意图
  -> Spring Boot 将脱敏事件写入本机 MySQL
  -> 兴趣画像与今日推荐更新
```

这不是读取小红书账号的完整浏览历史，不读取推荐 feed、收藏、通知、私信或账号凭据。

## 一次性准备

### 1. 填写本机 MySQL 配置

直接编辑 [`../config/application-mysql-local.yml`](../config/application-mysql-local.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/habit_assistant?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_mysql_user
    password: your_mysql_password
```

该文件已被 `.gitignore` 忽略。账号和密码只保存在本机，不要提交到 Git，也不要粘贴到聊天或日志。
MySQL 用户需要能够访问 `habit_assistant`；若没有创建数据库权限，请先手动创建数据库。

不希望密码落盘时，可改用：

```powershell
.\scripts\start-demo-mysql.ps1 -MysqlUser root
```

脚本会以安全输入框读取密码，只保留在当前进程内存。

### 2. 检查 JDK、Agent Reach 与 Codex

```powershell
$demoJdk = Get-ChildItem -Directory .\.local-tools\jdk17 | Select-Object -First 1
$env:JAVA_HOME = $demoJdk.FullName
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
java -version

$demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $demoPython .\scripts\agent_reach_adapter.py --doctor
codex --version
```

小红书演示至少要求 doctor 输出 `opencli=true`。B站和 YouTube 分别使用已安装的 `bili`、
`yt-dlp`；顶层 `agent-reach` 命令不是当前直连 channel 路由的前置条件。

### 3. 准备本人会话和链接

在 Chrome 中登录自己的小红书小号，确认 OpenCLI 浏览器扩展已连接。不要导出 Cookie、Token
或 Session。可直接准备小红书的完整分享文案或带签名的长链接：Vue 会把它仅用于打开本地
Chrome 标签页，同时发送不含 `xsec_token` 的规范公开 URL 给后端。OpenCLI 随后绑定当前标签页，
只有标签页中的 noteId 与提交任务一致时才投影标题、作者和公开正文。读取失败时不会再自动
启动标题搜索或打开额外页面，而是提示重新打开目标笔记。签名参数始终不会进入请求、Codex、数据库或日志。

## 构建与启动

Vue 生产产物已提交到 Spring Boot 静态资源目录。修改过 `frontend/` 时执行：

```powershell
Set-Location .\frontend
npm.cmd install --cache ..\.npm-cache
npm.cmd run build
Set-Location ..
```

构建并启动服务：

```powershell
mvn package -DskipTests
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\habit-assistant-java-0.0.1-SNAPSHOT.jar
```

项目默认启用 `mysql` profile。看到 `Tomcat started on port 8080` 后打开
`http://localhost:8080`，右上角应显示“MySQL 演示服务在线”。若不是 MySQL，先检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/demo-system/status
```

## 现场操作

1. 用户 ID 填一个独立值，例如 `xhs-demo`。
2. 将完整分享文案或公开 URL 粘贴到输入框，确认下方出现“已识别并脱敏”。请允许
   `localhost` 打开小红书标签页，并保持该页面为最近打开的 Chrome 页面。
3. 可选填写关注点。
4. 勾选“我确认这是本人访问的公开内容”。
5. 点击“授权并开始 AI 分析”。
6. 新 PowerShell 会依次打印：

   ```text
   [PIPELINE 1/3] Reading the authorized public URL with Agent Reach...
   [PIPELINE 2/3] Running Codex semantic analysis...
   [PIPELINE 3/3] Saving the analyzed visit and refreshing the demo snapshot...
   ```

7. 页面会轮询当前任务。访问入队、Agent Reach、Codex AI、兴趣画像、今日推荐五步都完成后，
   再讲解公开访问证据、AI 摘要、兴趣标签和推荐理由。

页面授权已经是明确的单次确认，因此自动打开的 Worker 不再要求重复输入 `y`；PowerShell 保留
完整阶段日志，便于演示和排障。每次只精确处理刚创建的 `taskId`，不会顺手批量领取旧任务。

## 手动 Worker 备用命令

若 Windows 不允许网页打开 PowerShell，可手动运行。该方式会在终端请求一次 `y`：

```powershell
$demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $demoPython .\scripts\codex_query_worker.py `
  --once `
  --base-url http://localhost:8080 `
  --agent-reach-mode live `
  --allow-authenticated-browser `
  --verbose
```

## 验收清单

- 页面右上角显示 `MySQL 演示服务在线`。
- 分享文案能提取 URL，预览中不含 `xsec_token`、`token`、`source` 等参数。
- PowerShell 三个阶段都有日志，且 Agent Reach 与 Codex 均显示 `SUCCESS`。
- 页面不显示后端原始异常或整段 JSON；单个聚合模块失败时显示“安全降级”，其他结果仍可查看。
- 最近公开访问包含真实标题/摘要和 `HIGH` 或 `MEDIUM` 证据。
- 画像含稳定兴趣标签，今日推荐含推荐理由。
- MySQL 中存在本次用户的 `agent_query_task`、`user_activity`、`interest_term`、`recommendation` 数据。
- 数据库和日志中不存在 Cookie、Token、Session、Authorization Header 或账号密码。

## 常见故障

| 现象 | 处理 |
| --- | --- |
| 启动时提示 MySQL 认证失败 | 检查本地 YAML 的账号、密码、端口以及数据库权限 |
| 页面一直“正在连接本地服务” | 检查 `/actuator/health` 与 `/api/v1/demo-system/status` |
| Agent Reach 失败 | 确认 Chrome 已登录、OpenCLI 扩展已连接、允许 localhost 弹出标签页；打开目标笔记后重新创建任务 |
| Codex 失败 | 执行 `codex --version` 并确认 CLI 已登录；查看 PowerShell 第 2 阶段日志 |
| 有访问但画像为空 | 只有真实页面内容或中高置信度证据才形成稳定画像；失败占位数据不会冒充成功 |
| 推荐为空 | 点击“使用最新画像重算推荐”，并确认本次访问时间在最近 24 小时内 |
| 单个模块报错 | 聚合 API 会返回 HTTP 200 的部分结果和 `degraded=true`，不让整页因一个模块崩溃 |

## 安全表述

- 只分析用户主动提交、本人授权的公开链接。
- 浏览器登录态由 Chrome/OpenCLI 本地管理，后端不接收或保存凭据。
- 所有 URL 在入库和进入 Codex prompt 前都会移除敏感/跟踪参数。
- 不读取聊天、私信、通讯录、支付记录、验证码或平台私有接口。
- 不绕过验证码，不批量抓取受保护内容。
