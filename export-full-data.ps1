param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputPath = ".\data\full-backup.json"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$backup = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/admin/migration/full/export"
$json = $backup | ConvertTo-Json -Depth 30
$parent = Split-Path -Parent $OutputPath
if ($parent -and -not (Test-Path $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
$fullPath = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.File]::WriteAllText($fullPath, $json, [System.Text.Encoding]::UTF8)

Write-Host "Exported full backup to $fullPath"
Write-Host "Activities: $($backup.activities.count), contents: $($backup.contentCount), recommendations: $($backup.recommendationCount)"
