# 项目目标完成度评估（2026-07-22）

## 结论

项目已经具备“授权任务、行为事件入库、画像构建、推荐生成”的后端骨架，
但原目标目前仍是**部分达成**，还不能宣称真实小红书/多平台链路已经完整上线。

本次修改前的核心缺口是：Agent Reach 适配器只生成 mock 占位内容，却标记为
`HIGH / PAGE_VISIBLE_CONTENT`；Codex 分析虽然异步启动，但 worker 在分析结束前就
把占位事件写入后端。两者都会让画像使用不可靠信号。

本次修改后，真实 URL 已按 Agent Reach 的 channel 约定路由，工具失败会保留原始
浏览历史置信度；worker 也会在 Codex 分析结束后再入库。距离完整目标主要还差
真实小红书登录会话下的端到端验收。

## 完成度矩阵

| 目标 | 状态 | 当前证据 | 剩余工作 |
| --- | --- | --- | --- |
| 用户授权后采集 | 已达成 MVP | Vue 单次授权、loopback Worker、精确 `taskId` 领取；`--yes` 只由已确认的本机请求传入 | 托盘常驻与细粒度授权撤销尚未产品化 |
| 浏览器历史和用户分享 URL | 已达成 MVP | 浏览历史导入脚本、public URL task、统一 behavior batch | 用真实个人样本做一次验收 |
| Agent Reach 真实读取 | 基本达成 | Python 现在调用 OpenCLI/bili-cli/yt-dlp/Jina Reader，并有 `--doctor` | 当前机器已安装 OpenCLI、bili-cli 0.6.2、yt-dlp 2026.07.04；B站真实元数据已验证，YouTube 当前网络烟雾测试超时并正确降级；`agent-reach` 总入口仍未安装，但直接 channel 路由可用 |
| 小红书登录态读取 | 部分达成 | 显式 `--allow-authenticated-browser` 后调用本机 OpenCLI 读取已提供的公开笔记 URL | 需 Chrome/OpenCLI 扩展已有登录态，并用真实 URL 验证 |
| Codex CLI 语义分析 | 基本达成 | 使用非交互 `codex exec`、只读沙箱、临时会话和隔离目录；入库前等待结果 | 需在实际登录的 Codex CLI 上跑完整任务 |
| 后端数据库记录用户行为 | 已达成 MVP | 默认 MySQL profile、本地私密 YAML、显式 schema、`/api/v1/behavior-events/batch` | 生产鉴权与数据保留策略仍需上线配置 |
| 形成个人画像 | 已达成 MVP | `UserProfileBuilder` / `ProfileService` 从行为和标签生成画像与证据 | 真实数据质量、冷启动和长期衰减需要评估 |
| 个性化每日推荐 | 部分达成 | `RecommendationService` 根据画像、反馈和内容候选打分 | 内容候选仍以现有 collector/查询词为主，尚不是完整平台内容推荐产品 |
| 演示界面与状态证据 | 已达成 MVP | 首页提供五阶段就绪状态、访问证据、Agent Reach/Codex 状态、画像和推荐；后端提供聚合快照 API | 仍需用本人真实公开笔记 URL 做现场验收 |
| 全链路自动每日运行 | 未完全达成 | 已有 daily job 和 worker 轮询能力 | 本地 worker 常驻、调度、失败重试和用户通知需要产品化 |

## 当前安全边界

- 后端和 Python 脚本不接收、不保存 Cookie、Token、Session 或账号密码。
- 小红书登录态由 OpenCLI/浏览器在本机管理，项目只接收脱敏后的公开页面结果。
- 小红书 URL 中的 `xsec_token` 在创建任务时就被删除，不传给 OpenCLI、Codex、日志或数据库；演示优先使用分享文案中的 `xhslink.com` 短链接。
- 未显式传入 `--allow-authenticated-browser` 时，不读取任何登录态页面。
- 不自动读取收藏、通知、私信或整个个性化 feed。若未来扩展 feed，必须增加独立授权范围、
  最小条数限制和测试；私信/通知仍应保持禁止。
- 只有真实工具成功返回公开内容时才提升到 `HIGH / PAGE_VISIBLE_CONTENT`。

## 推荐的真实验收步骤

1. 使用项目已准备的本地 JDK 17 并执行测试；当前全量 Java 测试为 41 个，Python 测试为 48 个：

   ```powershell
   $demoJdk = Get-ChildItem -Directory .\.local-tools\jdk17 | Select-Object -First 1
   $env:JAVA_HOME = $demoJdk.FullName
   $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
   mvn test
   ```

2. 使用 Codex 自带 Python 检查 Agent Reach channel（当前应显示 `opencli/bili/yt-dlp/curl=true`）：

   ```powershell
   $demoPython = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
   & $demoPython scripts/agent_reach_adapter.py --doctor
   ```

3. 在 Chrome 中登录小红书并确认 OpenCLI 扩展可用；不要导出 Cookie。
4. 在后端创建一个只包含本人授权公开笔记 URL 的任务。
5. 启动 worker：

   ```powershell
   & $demoPython scripts/codex_query_worker.py --once --direct-behavior-batch --allow-authenticated-browser --verbose
   ```

6. 验证数据库中的行为事件包含真实标题/摘要、`adapterMode=live`、
   `llm_status=SUCCESS`，且 URL/metadata 中不存在 `xsec_token`、Cookie、Token、Session。
7. 调用画像和今日推荐接口，确认推荐理由能关联到这条真实访问证据。

完成以上步骤后，才可以把“小红书公开 URL → Agent Reach/OpenCLI → Codex →
后端数据库 → 画像 → 每日推荐”标记为端到端达成。
