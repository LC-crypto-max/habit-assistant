param(
    [string]$MysqlHost = "localhost",
    [int]$MysqlPort = 3306,
    [string]$MysqlDatabase = "habit_assistant",
    [string]$MysqlUser = "root",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if ($MysqlDatabase -notmatch '^[A-Za-z0-9_]+$') {
    throw "MYSQL_DATABASE 只能包含字母、数字和下划线。"
}

function Test-TcpPort {
    param([string]$HostName, [int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect($HostName, $Port, $null, $null)
        return $pending.AsyncWaitHandle.WaitOne(1500) -and $client.Connected
    } finally {
        $client.Dispose()
    }
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
        throw "无法读取 Java 版本，exitCode=$($process.ExitCode)。"
    }
    return "$errorOutput`n$output".Trim()
}

if (-not (Test-TcpPort -HostName $MysqlHost -Port $MysqlPort)) {
    throw "本地 MySQL 未监听 ${MysqlHost}:${MysqlPort}。请先启动 MySQL80 服务。"
}

$mysql = (Get-Command mysql -ErrorAction Stop).Source
$bundledJava = Get-ChildItem -LiteralPath ".\.local-tools\jdk17" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue `
    | Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } `
    | Select-Object -First 1
$java = if ($bundledJava) { $bundledJava.FullName } else { (Get-Command java -ErrorAction Stop).Source }
$javaVersionText = Get-JavaVersionText -JavaPath $java
if ($javaVersionText -notmatch 'version "(1[7-9]|[2-9][0-9])') {
    throw "演示服务需要 JDK 17 或更高版本，当前检测到：$javaVersionText"
}
$javaHome = Split-Path -Parent (Split-Path -Parent $java)
$securePassword = Read-Host "请输入本机 MySQL 用户 $MysqlUser 的密码（仅保存在当前进程内存）" -AsSecureString
$credential = [System.Management.Automation.PSCredential]::new($MysqlUser, $securePassword)
$plainPassword = $credential.GetNetworkCredential().Password

$oldValues = @{}
foreach ($name in @("MYSQL_HOST", "MYSQL_PORT", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_PWD", "SPRING_CONFIG_IMPORT", "JAVA_HOME", "Path")) {
    $oldValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    $env:MYSQL_HOST = $MysqlHost
    $env:MYSQL_PORT = [string]$MysqlPort
    $env:MYSQL_DATABASE = $MysqlDatabase
    $env:MYSQL_USER = $MysqlUser
    $env:MYSQL_PASSWORD = $plainPassword
    $env:MYSQL_PWD = $plainPassword
    # 本脚本使用当前进程中的临时凭据，避免本地 YAML 覆盖本次输入。
    $env:SPRING_CONFIG_IMPORT = "optional:classpath:codex-data-agent.yml"
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$($env:Path)"

    & $mysql --protocol=TCP --host=$MysqlHost --port=$MysqlPort --user=$MysqlUser `
        --connect-timeout=5 --execute="SELECT 1;"
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL 登录失败。请检查用户名、密码和端口。"
    }

    & $mysql --protocol=TCP --host=$MysqlHost --port=$MysqlPort --user=$MysqlUser `
        --connect-timeout=5 --execute="CREATE DATABASE IF NOT EXISTS ``$MysqlDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) {
        throw "无法创建或访问数据库 $MysqlDatabase。请为该用户授予数据库权限。"
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

    if (-not $SkipBuild) {
        & mvn -q package "-DskipTests"
        if ($LASTEXITCODE -ne 0) {
            throw "Maven 构建失败，服务器未启动。"
        }
    }

    $jar = Resolve-Path ".\target\habit-assistant-java-0.0.1-SNAPSHOT.jar" -ErrorAction Stop
    Write-Host "MySQL 预检通过，正在启动演示服务：http://localhost:8080"
    & $java -jar $jar --spring.profiles.active=mysql
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot 异常退出，exitCode=$LASTEXITCODE。"
    }
} finally {
    $plainPassword = $null
    foreach ($name in $oldValues.Keys) {
        $previous = $oldValues[$name]
        if ($null -eq $previous) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous, "Process")
        }
    }
}
