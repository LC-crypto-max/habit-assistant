param(
    [string]$JarPath = ".\target\habit-assistant-java-0.0.1-SNAPSHOT.jar",
    [string]$BaseUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 15
)

$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Stop-Port8080 {
    $connections = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
    $pids = $connections | Where-Object { $_.OwningProcess } | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
        Write-Host "Stopping process on 8080: $pid"
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    Invoke-RestMethod `
        -Method Post `
        -Uri $Uri `
        -TimeoutSec 5 `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function Show-LastLogs {
    param([string]$OutLog, [string]$ErrLog)
    Write-Host "Last 100 lines of stdout:"
    if (Test-Path $OutLog) { Get-Content $OutLog -Encoding UTF8 -Tail 100 }
    Write-Host "Last 100 lines of stderr:"
    if (Test-Path $ErrLog) { Get-Content $ErrLog -Encoding UTF8 -Tail 100 }
}

if (-not (Test-Path $JarPath)) {
    throw "Jar not found: $JarPath. Run mvn package first."
}

if (-not (Test-Path ".\logs")) {
    New-Item -ItemType Directory -Path ".\logs" | Out-Null
}

$outLog = [System.IO.Path]::GetFullPath(".\logs\verify-app.out.log")
$errLog = [System.IO.Path]::GetFullPath(".\logs\verify-app.err.log")
Remove-Item -LiteralPath $outLog -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $errLog -Force -ErrorAction SilentlyContinue

$process = $null
$stdoutWriter = $null
$stderrWriter = $null

try {
    Stop-Port8080

    $javaPath = (Get-Command java -ErrorAction Stop).Source
    $appArguments = @(
        "-jar",
        "`"$([System.IO.Path]::GetFullPath($JarPath))`"",
        "--spring.datasource.url=`"jdbc:h2:mem:habit_verify;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false`"",
        "--spring.jpa.hibernate.ddl-auto=create-drop",
        "--spring.main.lazy-initialization=true",
        "--assistant.daily-cron=-",
        "--assistant.history-import.enabled=false",
        "--assistant.history-import.cron=-",
        "--assistant.collectors.rss.enabled=false",
        "--assistant.collectors.json-api.enabled=false",
        "--assistant.collectors.x.enabled=false",
        "--assistant.feishu.webhook-url="
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $javaPath
    $startInfo.Arguments = ($appArguments -join " ")
    $startInfo.WorkingDirectory = (Get-Location).Path
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $stdoutWriter = New-Object System.IO.StreamWriter($outLog, $false, [System.Text.Encoding]::UTF8)
    $stderrWriter = New-Object System.IO.StreamWriter($errLog, $false, [System.Text.Encoding]::UTF8)
    $stdoutWriter.AutoFlush = $true
    $stderrWriter.AutoFlush = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $process.EnableRaisingEvents = $true
    $process.add_OutputDataReceived({
        if ($EventArgs.Data -ne $null) {
            $stdoutWriter.WriteLine($EventArgs.Data)
        }
    })
    $process.add_ErrorDataReceived({
        if ($EventArgs.Data -ne $null) {
            $stderrWriter.WriteLine($EventArgs.Data)
        }
    })
    [void]$process.Start()
    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()

    Write-Host "Started app pid=$($process.Id)"

    $healthy = $false
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq "UP") {
                $healthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }

    if (-not $healthy) {
        Show-LastLogs -OutLog $outLog -ErrLog $errLog
        throw "Application did not become healthy within $StartupTimeoutSeconds seconds"
    }

    Write-Host "Health check passed"

    $userId = "verify-user"
    Invoke-JsonPost -Uri "$BaseUrl/api/visits" -Body @{
        userId = $userId
        title = "本地验证访问记录"
        url = "https://example.com/verify"
        platform = "verify"
        tags = @("验证", "Java")
    } | Out-Null

    Invoke-JsonPost -Uri "$BaseUrl/api/habits/submit" -Body @{
        nickname = "验证用户"
        habitName = "阅读打卡"
        content = "通过 verify-local.ps1 验证在线提交接口"
        recordDate = (Get-Date).ToString("yyyy-MM-dd")
        remark = "后台启动自动验证"
    } | Out-Null

    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/recommendations/generate?userId=$userId" -TimeoutSec 5 | Out-Null
    Invoke-JsonPost -Uri "$BaseUrl/api/recommendations/search" -Body @{
        userId = $userId
        keyword = "AI 编程"
        platform = "verify"
        refresh = $true
    } | Out-Null
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/mini/users/$userId/dashboard" -TimeoutSec 5 | Out-Null
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/history-import/run" -TimeoutSec 5 | Out-Null
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/admin/migration/activities/export" -TimeoutSec 5 | Out-Null
    Invoke-JsonPost -Uri "$BaseUrl/api/integrations/feishu/push-recommendations" -Body @{
        userId = $userId
        webhookUrl = ""
    } | Out-Null

    Write-Host "Local API verification passed"
} finally {
    if ($process -and -not $process.HasExited) {
        Write-Host "Stopping app pid=$($process.Id)"
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        $process.WaitForExit(5000) | Out-Null
    }
    if ($stdoutWriter) { $stdoutWriter.Dispose() }
    if ($stderrWriter) { $stderrWriter.Dispose() }
}
