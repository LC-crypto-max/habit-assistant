param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UserId = "demo-user"
)

$ErrorActionPreference = "Stop"

chcp 65001 | Out-Null
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-JsonPost {
    param(
        [string]$Url,
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

    Invoke-RestMethod `
        -Uri $Url `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body $bytes
}

function Show-Json {
    param([object]$Value)
    $Value | ConvertTo-Json -Depth 20
}

$encodedUserId = [uri]::EscapeDataString($UserId)

Write-Host "Step 1: Health check -> $BaseUrl/actuator/health"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Show-Json $health

Write-Host "`nStep 2: Create Chinese search term for user $UserId"
$searchResult = Invoke-JsonPost `
    -Url "$BaseUrl/api/search-terms" `
    -Body @{
        userId = $UserId
        keyword = "微信小程序 Spring Boot 推荐系统"
        platform = "baidu"
    }
Show-Json $searchResult

Write-Host "`nStep 3: Create watching activity"
$activityResult = Invoke-JsonPost `
    -Url "$BaseUrl/api/activities" `
    -Body @{
        userId = $UserId
        type = "WATCH"
        platform = "bilibili"
        title = "H2 + JPA 快速搭建本地原型"
        url = "https://www.bilibili.com/video/example"
        text = "适合本地 MVP 使用，先快速验证数据模型、推荐接口和用户画像"
        tags = @("Java", "JPA", "H2", "Spring Boot", "用户画像", "推荐系统")
    }
Show-Json $activityResult

Write-Host "`nStep 4: Create favorite activity"
$favoriteResult = Invoke-JsonPost `
    -Url "$BaseUrl/api/activities" `
    -Body @{
        userId = $UserId
        type = "FAVORITE"
        platform = "bilibili"
        title = "个人兴趣助手和 AI Agent 工作流"
        url = "https://www.bilibili.com/video/agent-demo"
        text = "收藏一条关于 AI Agent、个人自动化、内容推荐和飞书推送的内容"
        tags = @("AI", "Agent", "飞书", "自动化", "推荐")
    }
Show-Json $favoriteResult

Write-Host "`nStep 5: Create real visit record"
$visitResult = Invoke-JsonPost `
    -Url "$BaseUrl/api/visits" `
    -Body @{
        userId = $UserId
        title = "OpenAI API 文档与 Spring Boot 集成思路"
        url = "https://platform.openai.com/docs"
        platform = "openai"
        tags = @("AI", "OpenAI", "API", "Spring Boot")
    }
Show-Json $visitResult

Write-Host "`nStep 6: Get profile"
$profile = Invoke-RestMethod -Uri "$BaseUrl/api/profile?userId=$encodedUserId" -Method Get
Show-Json $profile

Write-Host "`nStep 7: Generate today's recommendations"
$generated = Invoke-RestMethod -Uri "$BaseUrl/api/recommendations/generate?userId=$encodedUserId" -Method Post
Show-Json $generated

Write-Host "`nStep 8: Get mini-program dashboard"
$miniDashboard = Invoke-RestMethod -Uri "$BaseUrl/api/mini/users/$encodedUserId/dashboard" -Method Get
Show-Json $miniDashboard

Write-Host "`nStep 9: Export migration backup"
$backup = Invoke-RestMethod -Uri "$BaseUrl/api/admin/migration/activities/export" -Method Get
Show-Json @{ count = $backup.count }

Write-Host "`nStep 10: Try Feishu push without webhook"
$feishuResult = Invoke-JsonPost `
    -Url "$BaseUrl/api/integrations/feishu/push-recommendations" `
    -Body @{
        userId = $UserId
        webhookUrl = ""
    }
Show-Json $feishuResult

Write-Host "`nTest finished."
