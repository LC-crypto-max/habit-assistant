param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UserId = "me",
    [string]$ProcessName = "bilibili",
    [string]$Platform = "bilibili-app"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-AssistantJson {
    param([string]$Path, [object]$Body)
    $json = $Body | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -ContentType "application/json; charset=utf-8" -Body $json -TimeoutSec 8
}

$processes = Get-Process -ErrorAction SilentlyContinue |
    Where-Object {
        $_.ProcessName -like "*$ProcessName*" -or
        $_.MainWindowTitle -like "*$ProcessName*" -or
        $_.MainWindowTitle -like "*哔哩哔哩*" -or
        $_.MainWindowTitle -like "*Bilibili*"
    }

if (-not $processes) {
    [PSCustomObject]@{
        userId = $UserId
        platform = $Platform
        recorded = $false
        message = "未检测到匹配进程，未上报行为。"
    }
    exit 0
}

$first = $processes | Select-Object -First 1
$title = if ([string]::IsNullOrWhiteSpace($first.MainWindowTitle)) {
    "检测到正在使用 $($first.ProcessName)"
} else {
    "检测到正在使用 $($first.MainWindowTitle)"
}

$response = Invoke-AssistantJson "/api/activities" @{
    userId = $UserId
    type = "WATCH"
    platform = $Platform
    title = $title
    url = ""
    text = "$title process=$($first.ProcessName)"
    occurredAt = (Get-Date).ToString("o")
    tags = @("app-usage", "bilibili")
}

[PSCustomObject]@{
    userId = $UserId
    platform = $Platform
    recorded = $true
    activityId = $response.id
    title = $response.title
}
