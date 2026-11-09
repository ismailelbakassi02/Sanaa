# Stop the local SANAA application processes started by dev-start.ps1.
# Compatible with PowerShell 5.1. This file is intentionally ASCII-only.
param(
    [switch]$Infra
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'docker\docker-compose.yml'
$PidFile = Join-Path $Root 'logs\pids.txt'

if (Test-Path $PidFile) {
    foreach ($line in Get-Content $PidFile) {
        if ($line -match '^\s*([^=]+)=(\d+)\s*$') {
            $name = $matches[1]
            $processId = [int]$matches[2]
            $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if (-not $process) {
                Write-Host "$name ($processId) is not running."
                continue
            }
            & taskkill /PID $processId /T /F 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Stopped $name ($processId)."
            } else {
                Write-Host "Could not stop $name ($processId)."
            }
        }
    }
    Remove-Item $PidFile -Force
} else {
    Write-Host 'No logs\pids.txt found; nothing to stop.'
}

if ($Infra) {
    if (-not (Test-Path $EnvFile)) {
        Write-Host 'No .env file found; skipping Docker Compose.'
    } else {
        Write-Host 'Stopping Docker infrastructure...'
        & docker compose --env-file $EnvFile -f $ComposeFile --profile infra stop
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'docker compose stop failed.'
        }
    }
}
