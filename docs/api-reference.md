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
| `GET` | `/api/activities/recent?userId=alice` | Get recent activities |
| `POST` | `/api/visits` | Record a visited link |
| `POST` | `/api/visits/import?userId=alice` | Import browser history CSV |

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
