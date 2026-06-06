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
