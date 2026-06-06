# 抖音数据源接入方案

## 目标

将用户在抖音上的搜索、观看、点赞、收藏和主动分享记录，沉淀为统一行为事件，进入现有画像和推荐链路。

## 合规边界

项目不做模拟登录、绕过风控、批量抓取个人主页或推荐流。推荐路线是：

- 用户主动提交抖音链接、标题、标签和备注。
- 用户主动上传 CSV/JSON 导出文件。
- 微信小程序或飞书机器人作为提交入口。
- 后续如有官方、企业或授权数据源，再实现专用 Collector。

统一平台标识：

```text
platform = douyin
```

## 批量行为上传

推荐使用统一行为接口：

```text
POST /api/v1/behavior-events/batch
```

PowerShell 示例：

```powershell
$body = @{
  events = @(
    @{
      userId = "alice"
      platform = "douyin"
      source = "manual-export"
      externalId = "douyin-aweme-001"
      type = "WATCH"
      title = "抖音 AI 工具短视频收藏"
      url = "https://www.douyin.com/search/AI%20效率%20工具"
      summary = "用户主动上传的抖音观看记录"
      tags = @("抖音", "AI", "效率", "短视频")
    }
  )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/behavior-events/batch" `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

## CSV 导入

示例文件：

```text
sample-douyin-visits.csv
```

导入方式：

```powershell
curl.exe -X POST "http://localhost:8080/api/visits/import?userId=alice" `
  -F "file=@sample-douyin-visits.csv"
```

## 小程序和飞书入口

小程序页面建议包含：

- 抖音链接
- 标题或备注
- 标签输入
- 行为类型：浏览、观看、点赞、收藏、不感兴趣

飞书机器人输入示例：

```text
抖音 https://www.douyin.com/... AI效率 短视频 收藏
```

解析为：

```json
{
  "platform": "douyin",
  "source": "feishu-bot",
  "type": "FAVORITE",
  "tags": ["AI效率", "短视频"]
}
```

## 推荐链路

当前 Mock 内容源已加入抖音候选内容：

- 抖音 AI 工具短视频
- 抖音生活方式/低脂晚餐
- 抖音 Java/Spring Boot 学习切片

用户提交 `douyin` 行为后，`GET /api/profile/daily` 会在平台分布和兴趣标签中体现抖音数据；推荐服务会根据标签命中返回相关候选内容。
