# 小红书数据源接入方案

## 目标

把用户在小红书上经常搜索、浏览、收藏或主动分享的内容沉淀为行为数据，进入现有用户画像链路，再由推荐服务生成更贴近用户生活方式、消费决策、兴趣灵感的内容推荐。

## 合规边界

小红书个人访问记录、收藏列表、推荐流等数据通常不提供通用的个人开放 API。项目不建议做以下事情：

- 模拟登录后批量抓取个人主页、收藏、搜索结果或推荐流。
- 绕过验证码、风控、反爬策略或平台访问限制。
- 未经用户授权读取、上传或分享用户的小红书隐私数据。
- 把第三方非授权接口当成稳定生产依赖。

推荐采用“用户主动提交 + 小程序/飞书入口 + 官方或授权 API 适配”的组合路线。

## 推荐路线

### 阶段 1：MVP，用户主动提交

适合本地验证和 5~10 人协作。

数据来源：

- 用户复制小红书笔记链接到前端页面。
- 用户上传 CSV。
- 用户在微信小程序里提交“小红书链接、标题、标签、备注”。
- 用户在飞书机器人里发送“小红书链接 + 关键词”。

落库方式：

- 访问类内容写入 `/api/visits`。
- 搜索词写入 `/api/search-terms`。
- 观看、收藏、喜欢、不感兴趣写入 `/api/activities`。
- 平台统一使用 `xiaohongshu`。

CSV 示例：

```csv
title,url,platform,time,tags
小红书AI效率笔记收藏,https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记,xiaohongshu,2026-06-04T09:00:00,小红书;AI;效率;笔记
```

本项目已提供：

```text
sample-xiaohongshu-visits.csv
```

导入方式：

```powershell
curl.exe -X POST "http://localhost:8080/api/visits/import?userId=alice" `
  -F "file=@sample-xiaohongshu-visits.csv"
```

或直接提交单条访问记录：

```powershell
$body = @{
  userId = "alice"
  title = "小红书AI效率笔记收藏"
  url = "https://www.xiaohongshu.com/search_result?keyword=AI%20效率%20笔记"
  platform = "xiaohongshu"
  tags = @("小红书", "AI", "效率", "笔记")
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/visits" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

### 阶段 2：微信小程序入口

适合多用户在线提交。

小程序页面建议：

- 文本框：小红书链接。
- 文本框：标题或备注。
- 标签输入：美食、穿搭、旅行、学习、AI、效率等。
- 操作类型：浏览、收藏、喜欢、不感兴趣。
- 按钮：提交到画像。

后端可复用现有接口：

| 场景 | 接口 |
| --- | --- |
| 上传小红书链接 | `POST /api/mini/users/{userId}/visits` |
| 上传小红书搜索词 | `POST /api/mini/users/{userId}/search-terms` |
| 上传收藏/喜欢/不感兴趣 | `POST /api/mini/users/{userId}/activities` |
| 获取画像和推荐 | `GET /api/mini/users/{userId}/dashboard` |

正式上线时，`userId` 应由微信登录态映射得到，不应长期信任前端手填。

### 阶段 3：飞书机器人入口

适合团队或个人日常使用。

交互设计：

```text
用户发送：
小红书 https://www.xiaohongshu.com/... AI效率 笔记 收藏

机器人解析：
platform = xiaohongshu
url = 链接
tags = ["AI效率", "笔记"]
type = FAVORITE
```

落库接口：

- 收藏/喜欢类内容调用 `POST /api/activities`。
- 普通链接调用 `POST /api/visits`。
- 每日推荐继续复用 `POST /api/integrations/feishu/push-recommendations`。

后续可以新增飞书事件回调 Controller，但第一阶段也可以用已有 API 先跑通。

### 阶段 4：官方或授权数据源适配

如果后续拿到小红书开放平台、营销开放平台、品牌合作、数据服务商或内部授权数据，可以把它适配成现有 Collector：

```text
XiaohongshuAuthorizedCollector implements PlatformCollector
```

输入：

- 用户兴趣词。
- 用户授权 token 或租户级访问凭证。
- 搜索关键词或内容分类。

输出：

- `CollectedContent(platform="xiaohongshu", title, url, author, summary, publishedAt, tags)`

这样不用改推荐服务，只增加一个合规内容源。

## 数据模型建议

当前模型已经可以承接小红书数据：

- `UserActivity.platform = xiaohongshu`
- `UserActivity.type = VISIT / SEARCH / LIKE / FAVORITE / DISLIKE`
- `UserActivity.title = 笔记标题`
- `UserActivity.url = 笔记链接或搜索链接`
- `UserActivity.tags = 用户标签或自动识别标签`

如果后续要做更精细的分析，可新增字段或实体：

- `sourceApp`：来源应用，例如 xiaohongshu、wechat-mini、feishu。
- `externalNoteId`：小红书笔记 ID。
- `authorName`：作者名。
- `topic`：话题。
- `mediaType`：图文、视频、直播、商品。
- `userActionWeight`：收藏、点赞、停留时长等权重。

## 推荐策略

小红书内容更偏生活方式和消费决策，推荐策略建议加入这些标签簇：

- 美食：低脂晚餐、一人食、咖啡、探店。
- 旅行：Citywalk、周末路线、展览、酒店。
- 学习：读书笔记、AI效率、知识管理。
- 穿搭：通勤、运动、季节、风格。
- 家居：收纳、装修、桌面、清洁。

现有推荐逻辑已经支持：

- 兴趣词命中。
- 搜索关键词临时加权。
- 新鲜度加分。
- 喜欢/收藏/不感兴趣反馈闭环。
- `BLOCK_SOURCE` 屏蔽同平台候选。

## 部署建议

### 本地验证

```powershell
mvn test
mvn spring-boot:run
```

导入示例数据：

```powershell
curl.exe -X POST "http://localhost:8080/api/visits/import?userId=alice" `
  -F "file=@sample-xiaohongshu-visits.csv"
```

生成推荐：

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/recommendations/refresh?userId=alice"
```

访问：

```text
http://localhost:8080/
```

### 小程序/飞书/线上协作

推荐架构：

```text
微信小程序 / 飞书机器人 / Web 页面
  -> HTTPS 域名
  -> Nginx
  -> Spring Boot
  -> MySQL
```

上线前必须补：

- 登录鉴权。
- 用户授权说明。
- 管理员接口保护。
- HTTPS。
- 数据库备份。
- 基础限流。

## 当前项目已落地内容

- 可用 `platform=xiaohongshu` 提交访问、搜索和行为。
- `sample-xiaohongshu-visits.csv` 可直接导入测试。
- Mock 内容源已加入三条小红书候选内容。
- 前端关键词搜索可以选择或手填小红书相关关键词。
- 推荐服务可基于小红书行为生成画像和推荐。
