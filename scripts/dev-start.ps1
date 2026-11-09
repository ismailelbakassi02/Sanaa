# Start the local SANAA stack on Windows.
# Compatible with PowerShell 5.1. This file is intentionally ASCII-only.
param(
    [switch]$Client
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'docker\docker-compose.yml'
$LogDir = Join-Path $Root 'logs'
$PidFile = Join-Path $LogDir 'pids.txt'

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy .env.example to .env and set the required values."
}

# Load the root .env without printing any value.
$loadedKeys = @()
foreach ($line in Get-Content $EnvFile) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') {
        continue
    }
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        $loadedKeys += $name
    }
}
Write-Host ("Loaded {0} variables from .env" -f $loadedKeys.Count)

foreach ($required in @('DEMO_PASSWORD', 'PLATFORM_ADMIN_PASSWORD')) {
    $value = [Environment]::GetEnvironmentVariable($required, 'Process')
    if (-not $value) {
        throw "Missing $required in $EnvFile. See .env.example."
    }
}

if (-not $env:JAVA_HOME) {
    $defaultJdk = 'C:\Program Files\Java\jdk-27.0.1.0.3'
    if (Test-Path $defaultJdk) {
        $env:JAVA_HOME = $defaultJdk
    }
}

$java = 'java'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) {
        $java = $candidate
    }
}

$composeArgs = @('compose', '--env-file', $EnvFile, '-f', $ComposeFile, '--profile', 'infra')

Write-Host 'Checking Docker...'
$dockerVersion = & docker version --format '{{.Server.Version}}' 2>$null
if ($LASTEXITCODE -ne 0 -or -not $dockerVersion) {
    throw 'Docker is not running. Start Docker Desktop and retry.'
}

function Get-ContainerId([string]$Service) {
    $id = & docker @composeArgs ps -q $Service 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $id) {
        return $null
    }
    return ($id | Select-Object -First 1).Trim()
}

function Test-ServiceHealthy([string]$Service) {
    $id = Get-ContainerId $Service
    if (-not $id) {
        return $false
    }
    $state = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id 2>$null
    if (-not $state) {
        return $false
    }
    $state = $state.Trim()
    return ($state -eq 'healthy' -or $state -eq 'running')
}

$services = @('mysql', 'redis', 'kafka', 'mailpit', 'prometheus')
$allHealthy = $true
foreach ($service in $services) {
    if (-not (Test-ServiceHealthy $service)) {
        $allHealthy = $false
    }
}

if (-not $allHealthy) {
    Write-Host 'Starting Docker infrastructure...'
    & docker @composeArgs up -d
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose up failed.'
    }
} else {
    Write-Host 'Docker infrastructure is already healthy.'
}

Write-Host 'Waiting for Docker infrastructure...'
$infraDeadline = (Get-Date).AddSeconds(240)
$infraReady = $false
while ((Get-Date) -lt $infraDeadline) {
    $infraReady = $true
    foreach ($service in $services) {
        if (-not (Test-ServiceHealthy $service)) {
            $infraReady = $false
        }
    }
    if ($infraReady) {
        break
    }
    Start-Sleep -Seconds 5
}
if (-not $infraReady) {
    throw 'Docker infrastructure did not become healthy within 240 seconds.'
}
Write-Host 'Docker infrastructure is healthy.'

$authJar = Join-Path $Root 'auth-service\target\auth-service-0.0.1-SNAPSHOT.jar'
$gatewayJar = Join-Path $Root 'gateway\target\gateway-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $authJar) -or -not (Test-Path $gatewayJar)) {
    Write-Host 'Building missing application jars...'
    Push-Location $Root
    try {
        & mvn -B -q -DskipTests -pl auth-service,gateway -am package
        $buildExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($buildExit -ne 0) {
        throw 'Maven build failed.'
    }
}
if (-not (Test-Path $authJar)) {
    throw "Missing $authJar after the build."
}
if (-not (Test-Path $gatewayJar)) {
    throw "Missing $gatewayJar after the build."
}

New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
if (Test-Path $PidFile) {
    $running = @()
    foreach ($line in Get-Content $PidFile) {
        if ($line -match '^\s*([^=]+)=(\d+)\s*$') {
            $existing = Get-Process -Id ([int]$matches[2]) -ErrorAction SilentlyContinue
            if ($existing) {
                $running += $matches[1]
            }
        }
    }
    if ($running.Count -gt 0) {
        throw 'Applications are already running. Run scripts\dev-stop.ps1 first.'
    }
    Remove-Item $PidFile -Force
}

foreach ($port in @(8080, 8081)) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        throw "Port $port is already in use. Run scripts\dev-stop.ps1 first."
    }
}

$pidEntries = @()

function Wait-Health([string]$Name, [string]$Url, [int]$ProcessId, [string]$LogPath) {
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if (-not $process) {
            if (Test-Path $LogPath) {
                Get-Content $LogPath -Tail 20 | ForEach-Object { Write-Host $_ }
            }
            throw "$Name exited before becoming healthy. See logs."
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            # Service is still starting.
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become healthy within 180 seconds. See logs."
}

Write-Host 'Starting auth-service...'
$env:SPRING_PROFILES_ACTIVE = 'demo,dev'
$env:MANAGEMENT_SERVER_PORT = '9081'
$authOut = Join-Path $LogDir 'auth-service.log'
$authErr = Join-Path $LogDir 'auth-service.err.log'
$authProcess = Start-Process -FilePath $java -ArgumentList @('-jar', $authJar) `
    -WorkingDirectory $Root -RedirectStandardOutput $authOut -RedirectStandardError $authErr `
    -PassThru -WindowStyle Hidden
$pidEntries += "auth-service=$($authProcess.Id)"
Set-Content -Path $PidFile -Value $pidEntries -Encoding ASCII

Write-Host 'Waiting for auth-service health...'
Wait-Health 'auth-service' 'http://localhost:9081/actuator/health' $authProcess.Id $authOut

Write-Host 'Starting gateway...'
$env:MANAGEMENT_SERVER_PORT = '9082'
$gatewayOut = Join-Path $LogDir 'gateway.log'
$gatewayErr = Join-Path $LogDir 'gateway.err.log'
$gatewayProcess = Start-Process -FilePath $java -ArgumentList @('-jar', $gatewayJar) `
    -WorkingDirectory $Root -RedirectStandardOutput $gatewayOut -RedirectStandardError $gatewayErr `
    -PassThru -WindowStyle Hidden
$pidEntries += "gateway=$($gatewayProcess.Id)"
Set-Content -Path $PidFile -Value $pidEntries -Encoding ASCII

Write-Host 'Waiting for gateway health...'
Wait-Health 'gateway' 'http://localhost:9082/actuator/health' $gatewayProcess.Id $gatewayOut

if ($Client) {
    $clientDir = Join-Path $Root 'client'
    if (-not (Test-Path (Join-Path $clientDir 'node_modules'))) {
        throw 'client\node_modules is missing. Run npm ci in client first.'
    }
    Write-Host 'Starting Vite...'
    $viteOut = Join-Path $LogDir 'vite.log'
    $viteErr = Join-Path $LogDir 'vite.err.log'
    $viteProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', 'npm run dev') `
        -WorkingDirectory $clientDir -RedirectStandardOutput $viteOut -RedirectStandardError $viteErr `
        -PassThru -WindowStyle Hidden
    $pidEntries += "client=$($viteProcess.Id)"
    Set-Content -Path $PidFile -Value $pidEntries -Encoding ASCII
    Wait-Health 'Vite' 'http://localhost:5173/' $viteProcess.Id $viteOut
}

Write-Host ''
Write-Host 'SANAA local stack'
Write-Host '-----------------'
Write-Host 'Gateway API      : http://localhost:8080'
Write-Host 'Auth service API : http://localhost:8081'
Write-Host 'Auth health      : http://localhost:9081/actuator/health'
Write-Host 'Gateway health   : http://localhost:9082/actuator/health'
if ($Client) {
    Write-Host 'Client           : http://localhost:5173'
} else {
    Write-Host 'Client           : not started (use -Client)'
}
Write-Host ''
Write-Host 'Demo accounts (passwords are in .env)'
Write-Host '  atlas    : admin@atlas.local, instructor@atlas.local, student1@atlas.local, student2@atlas.local'
Write-Host '  acme     : admin@acme.local, instructor@acme.local, student1@acme.local, student2@acme.local'
Write-Host '  platform : admin@platform.local (super admin)'
Write-Host ''
Write-Host "Logs and process ids: $LogDir"
