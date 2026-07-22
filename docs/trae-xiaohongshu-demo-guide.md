# TRAE CLI 小红书公开笔记演示手册

## 1. 演示目标

本演示证明：用户主动提交一条本人访问的小红书公开笔记后，系统能够在明确单次授权下读取页面可见公开内容，通过 TRAE CLI 生成结构化兴趣语义，再形成兴趣画像和今日推荐。

本演示不表示系统能够读取账号全部浏览历史、推荐 Feed、收藏、通知、评论、私信或其他私有数据。

主演示配置：

- 分析智能体：`TRAE CLI（推荐演示）`
- 内容分析渠道：`Agent Reach · 小红书公开笔记（主演示）`
- Worker 参数：`analysis-provider=trae`、`analysis-channel=agent-reach`
- Codex CLI：只作为现场备援

## 2. 演示链路

```text
公开分享文案或 URL
  -> Vue 删除后端 URL 中的签名和跟踪参数
  -> 原始分享链接只在 Chrome 中打开
  -> Spring Boot 创建指定 Agent Query Task
  -> 本地可见 PowerShell 启动 Worker
  -> Agent Reach/OpenCLI 读取匹配的可见公开标签页
  -> PolicyGate 检查事件是否允许进入 LLM
  -> TRAE CLI 在临时隔离目录分析脱敏上下文
  -> 严格 JSON 校验与 PrivacySanitizer
  -> behavior_event 入库
  -> 兴趣画像和今日推荐
```

## 3. 演示前检查

### 3.1 检查后端与数据库

推荐使用 MySQL 演示：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/v1/demo-system/status
```

期望：健康状态为 `UP`，页面右上角显示“MySQL 演示服务在线”。

### 3.2 检查 TRAE CLI

```powershell
& 'C:\Users\Lenovo\.local\bin\trae-cli.bat' --version
& 'C:\Users\Lenovo\.local\bin\trae-cli.bat' run --help
```

当前已验证 CLI 版本为 `0.1.0`。`C:\Users\Lenovo\trae-agent\trae_config.yaml` 至少需要：

```yaml
agents:
  trae_agent:
    enable_lakeview: false
    model: trae_agent_model

models:
  trae_agent_model:
    model_provider: openai
    model: gpt-4o
    max_tokens: 128000
    temperature: 0.5
    top_p: 1.0
    top_k: 0
    parallel_tool_calls: false
    max_retries: 0
```

`model_providers.openai.api_key` 必须替换为真实有效 Key。不要把 Key 写入项目、截图、日志或 Git。

### 3.3 检查 Agent Reach 小红书读取能力

```powershell
$env:PYTHONIOENCODING='utf-8'
$env:PYTHONUTF8='1'
py -3 scripts/agent_reach_adapter.py --doctor
```

小红书主演示至少需要 OpenCLI/可见标签页路由可用。演示前在 Chrome 中正常登录小红书，并确认 OpenCLI 浏览器扩展可读取当前标签页。

项目不要求把 Cookie 导出到 Agent Reach 配置，也不会保存 Cookie、Token 或 Session。

### 3.4 检查自动化测试

```powershell
py -3 -m unittest discover -s scripts -p 'test_*.py'
mvn "-Dnet.bytebuddy.experimental=true" test
```

当前基线：Python 63 项、Java 41 项通过。

## 4. 页面演示步骤

1. 启动 MySQL 和 Spring Boot。
2. 用 Chrome 登录小红书，并保持 OpenCLI 扩展可用。
3. 打开 `http://localhost:8080`。
4. 粘贴本人访问的一条真实公开笔记分享文案或完整 URL。
5. 可选填写关注点，例如“这篇笔记体现了哪些技术兴趣？”
6. “分析智能体”选择 `TRAE CLI（推荐演示）`。
7. “内容分析渠道”选择 `Agent Reach · 小红书公开笔记（主演示）`。
8. 勾选“我确认这是本人访问的公开内容”。
9. 点击“授权并开始 AI 分析”。
10. 浏览器会打开原始分享链接；保持该目标笔记标签页可见，不要快速切换或关闭。
11. PowerShell 窗口依次显示任务、Agent Reach、PolicyGate、TRAE 和后端写入日志。
12. 页面五阶段完成后，展示公开访问证据、AI 摘要、兴趣画像和今日推荐。

## 5. 成功判据

满足以下条件即可认为演示成功：

- Agent Query Task 为 `COMPLETED`。
- Agent Reach 路由状态为 `SUCCESS`。
- `rawMetadata.llm_status=SUCCESS`。
- `rawMetadata.llm_provider=trae`。
- `rawMetadata.analysis_channel=agent-reach`。
- 事件 `source=trae-cli-analysis`。
- 页面显示非空摘要和语义标签。
- 兴趣画像包含与公开笔记内容相关的标签。
- 今日推荐能够解释其与最新兴趣证据的关系。

## 6. 演示讲解话术

可以按下面顺序介绍：

1. “用户先主动提供一条本人访问的公开笔记，并进行单次授权。”
2. “Agent Reach 负责读取页面可见公开内容，TRAE 负责理解语义，两者职责分离。”
3. “原始签名链接只在浏览器中使用；后端和 TRAE 只接收脱敏后的规范 URL 与公开正文。”
4. “PolicyGate 决定哪些事件允许进入模型，APP 使用快照等低价值信号不会误触发分析。”
5. “TRAE 输出必须通过严格 JSON 和敏感字段校验，失败时不会编造 URL 或分析结果。”

TRAE 0.1.0 本身是软件工程 Agent。项目不会把公开笔记分析请求直接当成普通仓库 Bug 交给它，而是在隔离临时目录中生成 `ANALYSIS_INPUT.md` 和 `analysis-result.json` 契约，让 TRAE 只完成一次受限 JSON 转换。演示成功应以 worker 的 `[LLM ANALYSIS RESULT]`、`llm_status=SUCCESS` 和最终数据库记录为准，不以 TRAE 控制台出现 `Step completed` 为准。
6. “最终保存的是可解释兴趣证据，不是账号凭据或平台私有数据。”

## 7. 常见故障与降级

| 现象 | 处理方式 |
|---|---|
| TRAE 返回 401 | 检查 `trae_config.yaml` 中所选 Provider 的 API Key；不要在终端打印完整 Key |
| `show-config` 显示 `Set (your..._key)` | 这是示例占位 Key，不是网页登录凭据；替换为所选模型 Provider 的有效 API Key |
| TRAE 提示缺少模型字段 | 补充 `top_p`、`top_k`、`parallel_tool_calls`、`max_retries` |
| Lakeview 配置错误 | 未配置 Lakeview 时设置 `enable_lakeview: false` |
| TRAE 输出乱码或 `gbk codec can't encode` | 当前 Gateway 会在 TRAE 子进程边界强制 UTF-8；重启后端并从页面重新启动 Worker，确保加载的是新代码 |
| TRAE 有多个 Step 但提示非 JSON | 确认使用当前项目代码；新版 Gateway 从临时 `analysis-result.json` 读取结果，并会在错误详情中区分未写结果、鉴权和模型配置问题 |
| Agent Reach 未匹配笔记 | 保持刚打开的目标 Chrome 标签页可见，确认扩展和登录状态后重试 |
| `xhs login` 找不到 `a1` | 不在项目中导出 Cookie；运行适配器 `--doctor`，确认 `opencli=true`，并使用已登录 Chrome 的可见标签页路线 |
| URL 含 `xsec_token` | 正常现象；签名只用于浏览器打开，后端任务会自动移除 |
| TRAE 暂时不可用 | 页面将“分析智能体”切换为 `Codex CLI（备选）`，渠道仍保持 `agent-reach` |
| OpenCLI 暂时不可用 | 小红书 `agent-reach` 主链路会明确失败且不入库；临时展示只能主动切换为 `公开 URL 元数据（安全降级）` |
| MySQL 不可用 | 修复数据库后再演示；H2 只用于开发回归，不建议作为正式演示数据源 |

## 8. 命令行验证方式

正常页面会自动创建任务并启动 Worker。如果已经存在待处理任务，也可以手动运行：

```powershell
py -3 scripts/codex_query_worker.py --once --yes --verbose `
  --analysis-provider trae `
  --analysis-channel agent-reach `
  --agent-reach-mode live `
  --allow-authenticated-browser `
  --direct-behavior-batch
```

安全降级验证：

```powershell
py -3 scripts/codex_query_worker.py --once --yes --verbose `
  --analysis-provider trae `
  --analysis-channel public-metadata `
  --agent-reach-mode off `
  --direct-behavior-batch
```

## 9. 演示后检查

- 确认日志没有出现完整 Cookie、Token、Session、Authorization Header 或 API Key。
- 确认数据库 URL 不包含 `xsec_token`、`xsec_source` 等签名参数。
- 不需要导出或保存浏览器登录态。
- 如果演示使用了临时测试数据，可通过项目既有管理流程清理，不要直接删除数据库目录。

## 10. 相关文档

- [项目 README](../README.md)
- [技术沉淀](技术沉淀.md)
- [通用小红书演示指南](xiaohongshu-demo-guide.md)
- [Agent Task 架构](agent-task-architecture.md)
- [API Reference](api-reference.md)
