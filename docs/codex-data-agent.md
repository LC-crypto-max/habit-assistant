# Codex Data Agent

## 1. 模块目标

Codex Data Agent 是个人兴趣助手的授权式用户兴趣数据代理模块。它只处理用户授权范围内的非敏感访问行为数据，用于生成每日兴趣画像和推荐查询词。

## 2. 整体流程图

```text
用户授权
  -> Spring Boot 记录授权范围
  -> 本地数据代理 Agent 读取允许的数据源
  -> 采集非敏感访问行为
  -> PrivacySanitizer 脱敏与清洗
  -> InterestEvent
  -> DailyInterestProfile
  -> DailyRecommendation
```

## 3. 用户授权流程

用户调用 `POST /api/user-data-authorization/grant` 授权数据范围、App、域名和本地目录。授权过期或撤销后，系统拒绝继续采集。

## 4. 支持的数据源

- `APP_USAGE_SUMMARY`：App 名称、包名、使用时长、打开次数。
- `BROWSER_HISTORY`：公开 URL、标题、访问时间、访问次数。
- `PUBLIC_URL_METADATA`：公开网页 title、description、keywords。
- `LOCAL_NOTES`：授权目录下的 `.md`、`.txt`、`.csv`、`.json`、`.html`。
- `OFFICIAL_API`：预留官方 API / OAuth 数据源。

## 5. 不支持的高风险行为

不读取 Cookie、Token、Session、Authorization Header、账号密码、验证码、微信聊天数据库、聊天记录、私信、通讯录、支付记录、平台私有接口和受保护内容。

## 6. 为什么不直接读取 App 内部私密数据

App 内部消息、账号数据和私有接口通常包含敏感信息或平台受保护内容。该模块只关注兴趣画像所需的最小信号，例如“今天使用了某个 App 多久”或“访问了哪个公开 URL 标题”。

## 7. Android App Usage 上报方案

Android 客户端在用户授予 Usage Access 权限后，只上报 App 使用统计：

```http
POST /api/codex-agent/client/app-usage
```

接口不接收聊天内容、私信、Cookie、Token 或账号密码。

## 8. 浏览器历史方案

第一版读取 `data/imports/browser_history_sample.json`。后续可以扩展真实浏览器 SQLite History 文件读取，但只允许 URL、标题、访问时间和访问次数。

## 8.1 小红书采集能力分层

小红书数据采集不能等同于“直接读取 App 内部数据”。当前项目按三层能力处理：

Level 1：可见窗口快照

- 数据来源：Windows 当前可见窗口标题、进程名、浏览器窗口标题。
- 能力：判断用户是否正在使用小红书、Bilibili、YouTube 或浏览器相关窗口。
- 限制：不能获取完整访问历史，不能获取具体笔记详情、收藏、点赞、搜索记录，也不能读取 App 内部数据。
- 数据语义：`source=visible-window`，`confidence=LOW`，`dataLevel=APP_USAGE_SNAPSHOT`。

Level 2：浏览器历史

- 数据来源：用户授权范围内的 Chrome/Edge History SQLite 副本。
- 只读取：`title`、`url`、`visitTime`、`visitCount`。
- 只筛选授权域名，例如 `xiaohongshu.com`、`www.xiaohongshu.com`、`xhslink.com`。
- 数据语义：`source=browser-history`，`confidence=MEDIUM`，`dataLevel=BROWSER_HISTORY`。
- 可通过 `scripts/import-browser-history.ps1` 或后续 Python 导入脚本实现。

Level 3：浏览器插件 / 客户端上报

- 数据来源：用户安装并授权的浏览器插件或本地客户端。
- 插件只在授权域名下读取页面公开可见字段，例如标题、URL、公开作者、公开标签和页面摘要。
- 数据语义：`source=browser-extension`，`confidence=HIGH`，`dataLevel=PAGE_VISIBLE_CONTENT`。
- 后续可预留 `POST /api/v1/client/page-visit`，复用 DataPolicyChecker、PrivacySanitizer 和行为入库链路。

无论哪一层，都不得读取 Cookie、Token、Session、账号密码、私信、聊天记录、支付信息或平台受保护内容。

## 8.2 Windows 中文乱码处理

乱码主要来自 Windows 子进程输出，而不是 Spring Boot JSON。当前 worker 已尽量使用 Windows Unicode API 读取窗口标题，避免解析 PowerShell `Get-Process` 文本输出。

手动运行脚本时建议：

```powershell
chcp 65001
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
python .\scripts\codex_query_worker.py --once --base-url http://localhost:8080
```

Python 侧要求：

- `json.dumps(payload, ensure_ascii=False).encode("utf-8")`
- `Content-Type: application/json; charset=utf-8`
- `Accept: application/json`
- `sys.stdout/sys.stderr` 尽量 reconfigure 为 UTF-8。

## 9. REST API 使用方式

授权：

```http
POST /api/user-data-authorization/grant
GET /api/user-data-authorization/{userId}
POST /api/user-data-authorization/revoke
```

每日任务：

```http
POST /api/codex-agent/daily
POST /api/codex-agent/client/app-usage
```

## 10. 示例授权请求

```json
{
  "userId": "local_user",
  "grantedScopes": ["APP_USAGE_SUMMARY", "BROWSER_HISTORY", "PUBLIC_URL_METADATA", "LOCAL_NOTES"],
  "allowedApps": ["com.google.android.youtube", "tv.danmaku.bili"],
  "allowedDomains": ["youtube.com", "bilibili.com", "github.com", "juejin.cn"],
  "allowedPaths": ["data/raw", "data/imports", "data/local-notes"],
  "privacy": {
    "sanitize": true,
    "keepRawText": false,
    "allowSensitiveData": false
  },
  "expireAt": "2026-12-31T23:59:59+09:00"
}
```

## 11. 示例每日任务请求

```json
{
  "taskId": "daily_2026_06_06",
  "userId": "local_user",
  "action": "DAILY_INTEREST_COLLECT_AND_RECOMMEND",
  "date": "2026-06-06",
  "sources": ["APP_USAGE_SUMMARY", "BROWSER_HISTORY", "LOCAL_NOTES"],
  "maxRecords": 300,
  "recommendation": {
    "topK": 10,
    "language": "zh-CN",
    "categories": ["技术学习", "生活方式", "娱乐内容", "职业成长", "AI工具", "日语学习"]
  }
}
```

## 12. 示例响应

```json
{
  "status": "SUCCESS",
  "taskId": "daily_2026_06_06",
  "eventsCount": 8,
  "profilePath": "data/profiles/daily_profile_2026_06_06.json",
  "recommendationsPath": "data/recommendations/daily_recommendations_2026_06_06.json",
  "warnings": []
}
```

## 13. 如何查看兴趣事件

查看：

```text
data/processed/interest_events_YYYY_MM_DD.jsonl
```

每行是一个 `InterestEventDTO`。

## 14. 如何查看每日画像

查看：

```text
data/profiles/daily_profile_YYYY_MM_DD.json
```

## 15. 如何查看每日推荐

查看：

```text
data/recommendations/daily_recommendations_YYYY_MM_DD.json
```

第一版只生成推荐 query 和理由，不批量抓取平台内容。

## 16. 如何删除本地数据

删除以下目录中的本地数据：

```text
data/processed
data/profiles
data/recommendations
```

如需删除导入样例或本地笔记，可清理：

```text
data/imports
data/local-notes
```

## 17. 如何扩展新的平台或客户端

新增采集器时实现：

```java
DataCollector
```

要求：

- 先经过 `DataPolicyChecker`。
- 只输出公开、非敏感字段。
- 输出前经过 `PrivacySanitizer`。
- 新增测试和文档。

## 18. 隐私保护说明

模块默认脱敏手机号、邮箱、身份证号、银行卡号、Token、Cookie、Authorization Header 和地址类文本。任何涉及微信聊天记录、私信、通讯录、支付记录、验证码、平台私有接口和受保护内容的任务都会被拒绝。
