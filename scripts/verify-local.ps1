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

function Get-JavaVersionText {
    param([string]$JavaPath)
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaPath
    $startInfo.Arguments = "-version"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $output = $process.StandardOutput.ReadToEnd()
    $errorOutput = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "Unable to read Java version. exitCode=$($process.ExitCode)"
    }
    return "$errorOutput`n$output".Trim()
}

function Normalize-WindowsPathEnvironment {
    $variables = [Environment]::GetEnvironmentVariables("Process")
    $pathValue = $null
    foreach ($key in @($variables.Keys)) {
        if ([string]$key -ieq "Path") {
            if (-not $pathValue) {
                $pathValue = [string]$variables[$key]
            }
            [Environment]::SetEnvironmentVariable([string]$key, $null, "Process")
        }
    }
    if ($pathValue) {
        [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
    }
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

try {
    Stop-Port8080

    $bundledJava = Get-ChildItem -LiteralPath ".\.local-tools\jdk17" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue `
        | Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } `
        | Select-Object -First 1
    $javaPath = if ($bundledJava) { $bundledJava.FullName } else { (Get-Command java -ErrorAction Stop).Source }
    $javaVersionText = Get-JavaVersionText -JavaPath $javaPath
    if ($javaVersionText -notmatch 'version "(1[7-9]|[2-9][0-9])') {
        throw "Local verification requires JDK 17 or newer. Detected: $javaVersionText"
    }
    $appArguments = @(
        "-jar",
        "`"$([System.IO.Path]::GetFullPath($JarPath))`"",
        "--spring.profiles.active=h2",
        "--spring.datasource.url=`"jdbc:h2:mem:habit_verify;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false`"",
        "--spring.jpa.hibernate.ddl-auto=create-drop",
        "--spring.main.lazy-initialization=true",
        "--assistant.daily-cron=-",
        "--assistant.history-import.enabled=false",
        "--assistant.history-import.cron=-",
        "--assistant.history-import.directory=./target/verify-imports/history",
        "--assistant.history-import.archive-directory=./target/verify-imports/history/archive",
        "--assistant.history-import.failed-directory=./target/verify-imports/history/failed",
        "--assistant.collectors.rss.enabled=false",
        "--assistant.collectors.json-api.enabled=false",
        "--assistant.collectors.x.enabled=false",
        "--assistant.feishu.webhook-url="
    )

    Normalize-WindowsPathEnvironment
    $process = Start-Process `
        -FilePath $javaPath `
        -ArgumentList $appArguments `
        -WorkingDirectory (Get-Location).Path `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

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
}
