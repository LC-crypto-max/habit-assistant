param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputPath = ".\data\h2-activity-backup.json"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues["Invoke-RestMethod:Headers"] = @{ "Accept-Charset" = "utf-8" }

$backup = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/admin/migration/activities/export"
$json = $backup | ConvertTo-Json -Depth 20
$parent = Split-Path -Parent $OutputPath
if ($parent -and -not (Test-Path $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
$fullPath = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.File]::WriteAllText($fullPath, $json, [System.Text.Encoding]::UTF8)

Write-Host "Exported $($backup.count) activities to $fullPath"
