param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$InputPath = ".\data\full-backup.json"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path $InputPath)) {
    throw "Backup file not found: $InputPath"
}

$json = [System.IO.File]::ReadAllText((Resolve-Path $InputPath), [System.Text.Encoding]::UTF8)
$result = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/admin/migration/full/import" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($json))

Write-Host "Imported activities=$($result.activitiesImported), contents=$($result.contentsImported), recommendations=$($result.recommendationsImported)"
