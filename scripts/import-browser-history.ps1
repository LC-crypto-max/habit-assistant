param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UserId = "me",
    [ValidateSet("chrome", "edge")]
    [string]$Browser = "chrome",
    [int]$Limit = 200
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Resolve-HistoryPath {
    param([string]$BrowserName)
    if ($BrowserName -eq "edge") {
        return Join-Path $env:LOCALAPPDATA "Microsoft\Edge\User Data\Default\History"
    }
    return Join-Path $env:LOCALAPPDATA "Google\Chrome\User Data\Default\History"
}

function Convert-ChromeTime {
    param([Int64]$ChromeTime)
    if ($ChromeTime -le 0) {
        return (Get-Date).ToString("o")
    }
    return ([DateTime]::FromFileTimeUtc($ChromeTime * 10)).ToLocalTime().ToString("o")
}

function Invoke-AssistantJson {
    param([string]$Path, [object]$Body)
    $json = $Body | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -ContentType "application/json; charset=utf-8" -Body $json -TimeoutSec 8
}

$sqlite = Get-Command sqlite3 -ErrorAction SilentlyContinue
if (-not $sqlite) {
    throw "未找到 sqlite3 命令。请先安装 SQLite CLI，或从浏览器导出 CSV 后调用 /api/visits/import。"
}

$historyPath = Resolve-HistoryPath $Browser
if (-not (Test-Path $historyPath)) {
    throw "未找到浏览器历史文件：$historyPath"
}

$workDir = Join-Path (Get-Location) "data\browser-history-copy"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
$copyPath = Join-Path $workDir "$Browser-History-$(Get-Date -Format yyyyMMddHHmmss).sqlite"
Copy-Item -LiteralPath $historyPath -Destination $copyPath -Force

$query = "select title, url, last_visit_time from urls where url like 'http%' order by last_visit_time desc limit $Limit;"
$rows = & $sqlite.Source -readonly -separator "`t" $copyPath $query

$imported = 0
foreach ($row in $rows) {
    $parts = $row -split "`t", 3
    if ($parts.Count -lt 2) {
        continue
    }
    $title = if ([string]::IsNullOrWhiteSpace($parts[0])) { $parts[1] } else { $parts[0] }
    $visitedAt = if ($parts.Count -ge 3) { Convert-ChromeTime ([Int64]$parts[2]) } else { (Get-Date).ToString("o") }
    Invoke-AssistantJson "/api/visits" @{
        userId = $UserId
        title = $title
        url = $parts[1]
        platform = "browser-$Browser"
        visitedAt = $visitedAt
        tags = @("browser-history", $Browser)
    } | Out-Null
    $imported++
}

[PSCustomObject]@{
    browser = $Browser
    userId = $UserId
    imported = $imported
    copiedHistory = $copyPath
}
