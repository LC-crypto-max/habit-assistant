## Daily Profile

### GET /api/profile/daily

输出适合 HTML 网页、微信小程序或飞书机器人渲染的每日画像 JSON。

Query:

| Param | Required | Description |
| --- | --- | --- |
| `userId` | No | 用户标识，未传时使用默认用户 |
| `refresh` | No | `true` 时跳过缓存重新生成 |

Response:

```json
{
  "userId": "alice",
  "profileDate": "2026-06-04",
  "generatedAt": "2026-06-04T10:00:00",
  "summary": "用户 alice 最近 7 天产生 12 条行为，30 天累计 40 条。当前主要兴趣集中在 AI、Java、推荐系统。",
  "last7Days": {
    "days": 7,
    "activityCount": 12,
    "typeCounts": {"WATCH": 6, "SEARCH": 4},
    "platformCounts": {"bilibili": 5, "xiaohongshu": 4},
    "tags": [
      {"term": "AI", "weight": 10.5, "hitCount": 3, "lastSeenAt": "2026-06-04T09:00:00"}
    ]
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

缓存说明：

- 默认 `assistant.profile.cache.redis.enabled=false`，本地测试不依赖 Redis。
- 开启 Redis 后 key 格式为 `profile:daily:{userId}:{yyyy-MM-dd}`。
- `refresh=true` 会重新生成并覆盖当天缓存。

## Behavior Events

### POST /api/v1/behavior-events/batch

批量接收小红书、微信、B站等采集端上传的用户行为事件。接口会将事件转换为系统内部 `UserActivity`，写入当前数据源；当应用使用 MySQL profile 时会落入 MySQL。接口还会调用 `BehaviorEventPublisher`，在 RabbitMQ 开启时发送异步消息，供画像和推荐服务后续消费。

Request:

```json
{
  "events": [
    {
      "userId": "alice",
      "platform": "xiaohongshu",
      "source": "agent-reach",
      "externalId": "xhs-note-001",
      "type": "FAVORITE",
      "title": "小红书 AI 工作流笔记",
      "url": "https://www.xiaohongshu.com/explore/demo-note",
      "author": "作者名",
      "summary": "用户收藏的小红书效率笔记",
      "text": "补充备注",
      "occurredAt": "2026-06-04T09:00:00",
      "tags": ["小红书", "AI", "效率"]
    },
    {
      "userId": "alice",
      "platform": "wechat",
      "source": "wechat-mini",
      "externalId": "wechat-link-001",
      "type": "VISIT",
      "title": "微信公众号阅读清单",
      "url": "https://mp.weixin.qq.com/s/demo",
      "tags": ["微信", "阅读"]
    },
    {
      "userId": "alice",
      "platform": "bilibili",
      "source": "agent-reach",
      "externalId": "BV1demo",
      "type": "WATCH",
      "title": "B站 Spring Boot 推荐系统视频",
      "url": "https://www.bilibili.com/video/BV1demo",
      "tags": ["B站", "Java", "推荐系统"]
    }
  ]
}
```

Response:

```json
{
  "imported": 3,
  "skipped": 0,
  "messagesPublished": 0,
  "activities": [
    {
      "id": 1,
      "type": "FAVORITE",
      "platform": "xiaohongshu",
      "title": "小红书 AI 工作流笔记",
      "url": "https://www.xiaohongshu.com/explore/demo-note",
      "tags": ["xiaohongshu", "小红书", "AI", "效率"]
    }
  ]
}
```

字段说明：

| Field | Required | Description |
| --- | --- | --- |
| `events` | Yes | 批量事件数组，不能为空 |
| `events[].platform` | Yes | 平台标识，如 `xiaohongshu`、`wechat`、`bilibili` |
| `events[].type` | No | `SEARCH`、`VISIT`、`WATCH`、`LIKE`、`FAVORITE`、`DISLIKE`；为空时按 URL 推断 |
| `events[].source` | No | 采集来源，如 `agent-reach`、`wechat-mini`、`feishu-bot` |
| `events[].externalId` | No | 外部平台内容 ID，用于幂等扩展和追踪 |
| `events[].occurredAt` | No | 行为发生时间，未传则由服务端补当前时间 |

RabbitMQ 默认关闭，本地测试时 `messagesPublished` 通常为 `0`。生产环境开启：

```yaml
assistant:
  behavior:
    rabbitmq:
      enabled: true
      exchange: habit.behavior.events
      routing-key: behavior.events.created
```

# API Reference

## Base URL

本地直接访问：

```text
http://localhost:8080
```

通过 Nginx 访问：

```text
http://localhost:8088
```

## Error Format

参数校验失败：

```json
{
  "error": "VALIDATION_ERROR",
  "fields": {
    "fieldName": "must not be blank"
  }
}
```

JSON 或日期格式错误：

```json
{
  "error": "INVALID_REQUEST_BODY",
  "message": "请求体格式不正确，请检查 JSON 和日期格式"
}
```

## Habit Submission

### POST /api/habits/submit

提交一个小规模协作场景下的习惯打卡记录。

Request:

```json
{
  "nickname": "小李",
  "habitName": "阅读打卡",
  "content": "今天阅读了 Spring Boot 部署文档",
  "recordDate": "2026-05-31",
  "remark": "通过 Nginx 页面提交"
}
```

Validation:

| Field | Required | Limit |
| --- | --- | --- |
| `nickname` | Yes | 40 chars |
| `habitName` | Yes | 80 chars |
| `content` | Yes | 500 chars |
| `recordDate` | Yes | `yyyy-MM-dd` |
| `remark` | No | 300 chars |

Response:

```json
{
  "success": true,
  "message": "提交成功",
  "data": {
    "id": 1,
    "nickname": "小李",
    "habitName": "阅读打卡",
    "content": "今天阅读了 Spring Boot 部署文档",
    "recordDate": "2026-05-31",
    "remark": "通过 Nginx 页面提交",
    "createdAt": "2026-05-31T20:00:00"
  }
}
```

## Profile

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/profile/users` | List users found in activity data |
| `GET` | `/api/profile?userId=alice` | Get a user's profile terms |

## Activities

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/search-terms` | Record a search term |
| `POST` | `/api/activities` | Record a generic behavior |
| `POST` | `/api/datasources/events` | Record one normalized source event |
| `POST` | `/api/datasources/events/batch` | Record normalized source events in batch |
| `POST` | `/api/datasources/{platform}/events` | Record one event with platform from path |
| `GET` | `/api/activities/recent?userId=alice` | Get recent activities |
| `POST` | `/api/visits` | Record a visited link |
| `POST` | `/api/visits/import?userId=alice` | Import browser history CSV |

### POST /api/datasources/events

统一采集事件入口，适合小红书、B站、YouTube、微信、飞书机器人或本地客户端提交数据。

Request:

```json
{
  "userId": "alice",
  "platform": "xiaohongshu",
  "type": "FAVORITE",
  "externalId": "note-id",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "author": "作者名",
  "summary": "用户收藏的小红书效率笔记",
  "text": "补充备注",
  "occurredAt": "2026-06-04T09:00:00",
  "tags": ["小红书", "AI", "效率"]
}
```

Response:

```json
{
  "id": 1,
  "type": "FAVORITE",
  "platform": "xiaohongshu",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "tags": ["xiaohongshu", "小红书", "AI", "效率"]
}
```

### POST /api/datasources/events/batch

Request:

```json
{
  "events": [
    {
      "userId": "alice",
      "platform": "bilibili",
      "type": "WATCH",
      "title": "B站 Spring Boot 推荐系统视频",
      "url": "https://www.bilibili.com/video/BV1demo",
      "tags": ["B站", "Java", "推荐系统"]
    }
  ]
}
```

Response:

```json
{
  "imported": 1,
  "skipped": 0,
  "activities": []
}
```

### Xiaohongshu Data

小红书数据建议通过用户主动提交，不建议模拟登录或绕过平台限制抓取。提交时统一使用：

```json
{
  "userId": "alice",
  "title": "小红书AI效率笔记收藏",
  "url": "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记",
  "platform": "xiaohongshu",
  "tags": ["小红书", "AI", "效率", "笔记"]
}
```

可用接口：

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/visits` | Submit one Xiaohongshu note/search link |
| `POST` | `/api/visits/import?userId=alice` | Import `sample-xiaohongshu-visits.csv` |
| `POST` | `/api/search-terms` | Submit a Xiaohongshu search term |
| `POST` | `/api/activities` | Submit favorite/like/dislike behavior |

## Recommendations

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/recommendations/today?userId=alice` | Get recommendations; refresh automatically if expired |
| `POST` | `/api/recommendations/generate?userId=alice` | Generate recommendations without deleting existing items |
| `POST` | `/api/recommendations/refresh?userId=alice` | Replace today's recommendations |
| `POST` | `/api/recommendations/search` | Record a keyword and generate recommendations immediately |
| `GET` | `/api/recommendations/refresh-policy?userId=alice` | Explain refresh interval and expiration |
| `POST` | `/api/recommendations/{id}/feedback` | Submit recommendation feedback |

### POST /api/recommendations/search

Request:

```json
{
  "userId": "alice",
  "keyword": "AI 编程",
  "platform": "web-search",
  "refresh": true
}
```

Response:

```json
{
  "userId": "alice",
  "keyword": "AI 编程",
  "profile": {
    "userId": "alice",
    "interestTerms": []
  },
  "recommendations": [],
  "refreshPolicy": {
    "userId": "alice",
    "recentActivityCount": 3,
    "activityWindowHours": 24,
    "refreshHours": 6,
    "expired": false
  }
}
```

`refresh=true` 会先替换当天推荐，再返回最新结果；`refresh=false` 会按过期策略自动判断是否刷新。

## Mini Program

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/mini/users/{userId}/dashboard` | Profile and recommendations |
| `POST` | `/api/mini/users/{userId}/visits` | Upload a visit |
| `POST` | `/api/mini/users/{userId}/search-terms` | Upload a search term |
| `POST` | `/api/mini/users/{userId}/activities` | Upload a behavior |
| `POST` | `/api/mini/users/{userId}/recommendations/generate` | Generate recommendations |

## Integrations

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/integrations/feishu/push-recommendations` | Push current recommendations to Feishu bot |

## Migration

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/admin/migration/activities/export` | Export behavior backup |
| `POST` | `/api/admin/migration/activities/import` | Import behavior backup |
| `GET` | `/api/admin/migration/full/export` | Export behavior, content and recommendation backup |
| `POST` | `/api/admin/migration/full/import` | Import full backup |

## Local Pages

| URL | Description |
| --- | --- |
| `http://localhost:8088/` | Simple habit submission page served by Nginx |
| `http://localhost:8088/assistant/` | Full profile and recommendation UI proxied to Spring Boot |
| `http://localhost:8080/` | Full Spring Boot UI without Nginx |
