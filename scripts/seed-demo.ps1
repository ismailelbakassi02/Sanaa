# Seed the demo courses, modules and lessons through the public API.
# Compatible with PowerShell 5.1. This file is intentionally ASCII-only.
# The French course content lives as UTF-8 in demo-courses.json and is read with -Encoding UTF8.

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$DataFile = Join-Path $PSScriptRoot 'demo-courses.json'
$BaseUrl = 'http://localhost:8080'

# PowerShell 5.1 writes console output in the ANSI code page by default, which mangles accents.
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding -ArgumentList $false

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy .env.example to .env and set DEMO_PASSWORD."
}
if (-not (Test-Path $DataFile)) {
    throw "Missing $DataFile."
}

# Only DEMO_PASSWORD is needed. It is never printed and never written to a log.
$demoPassword = $null
foreach ($line in Get-Content $EnvFile) {
    if ($line -match '^\s*DEMO_PASSWORD\s*=\s*(.*)$') {
        $value = $matches[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $demoPassword = $value
    }
}
if (-not $demoPassword) {
    throw 'DEMO_PASSWORD is missing from .env. See .env.example.'
}

$data = Get-Content -Raw -Encoding UTF8 $DataFile | ConvertFrom-Json

# Fail fast on a data file that would produce an inconsistent demo, before any request is sent.
function Test-DemoData {
    foreach ($tenant in $data.tenants) {
        if (-not $tenant.slug -or -not $tenant.instructorEmail) {
            throw 'Every tenant needs a slug and an instructorEmail.'
        }
        if ($tenant.courses.Count -ne 3) {
            throw "Tenant '$($tenant.slug)' must declare exactly three courses."
        }
        $published = 0
        foreach ($course in $tenant.courses) {
            if (-not $course.title -or -not $course.description) {
                throw "A course of tenant '$($tenant.slug)' is missing a title or description."
            }
            if ($course.currency -ne 'MAD') {
                throw "Course '$($course.title)' must use MAD."
            }
            if ($course.price -lt 0) {
                throw "Course '$($course.title)' has a negative price."
            }
            if ($course.status -eq 'PUBLISHED') {
                $published++
            } elseif ($course.status -ne 'DRAFT') {
                throw "Course '$($course.title)' has an unknown status '$($course.status)'."
            }
            if ($course.modules.Count -ne 2) {
                throw "Course '$($course.title)' must declare exactly two modules."
            }
            $freeLessons = 0
            foreach ($module in $course.modules) {
                if (-not $module.title) {
                    throw "A module of course '$($course.title)' has no title."
                }
                if ($module.lessons.Count -lt 2 -or $module.lessons.Count -gt 3) {
                    throw "Module '$($module.title)' must declare two or three lessons."
                }
                foreach ($lesson in $module.lessons) {
                    if (-not $lesson.title) {
                        throw "A lesson of module '$($module.title)' has no title."
                    }
                    if ($lesson.durationSeconds -le 0) {
                        throw "Lesson '$($lesson.title)' needs a positive durationSeconds."
                    }
                    if ($lesson.freePreview -eq $true) {
                        $freeLessons++
                    }
                }
            }
            if ($freeLessons -ne 1) {
                throw "Course '$($course.title)' must have exactly one free-preview lesson."
            }
        }
        if ($published -ne 2) {
            throw "Tenant '$($tenant.slug)' must have exactly two PUBLISHED courses."
        }
    }
}

function Invoke-Gateway {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [string]$JsonBody
    )

    # A 429 means the gateway rate limiter rejected the call before it reached a service, so a
    # short retry cannot duplicate a write. Three attempts cover the demo seed burst.
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $curlArgs = @('-s', '-w', "`nHTTP_STATUS:%{http_code}`n", '-X', $Method, "$BaseUrl$Path")
        if ($Method -ne 'GET') {
            $curlArgs += @('-H', 'Content-Type: application/json')
        }
        if ($Token) {
            $curlArgs += @('-H', "Authorization: Bearer $Token")
        }

        $tempFile = $null
        if ($JsonBody) {
            # PowerShell strips embedded quotes when an inline JSON argument reaches a native
            # command, and Out-File adds a BOM. Write UTF-8 without BOM to a file instead.
            $tempFile = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllText(
                $tempFile, $JsonBody, (New-Object System.Text.UTF8Encoding -ArgumentList $false))
            $curlArgs += @('--data-binary', "@$tempFile")
        }

        try {
            $raw = (& curl.exe @curlArgs) -join "`n"
        } finally {
            if ($tempFile -and (Test-Path $tempFile)) {
                Remove-Item $tempFile -Force
            }
        }

        $statusMatch = [regex]::Match($raw, 'HTTP_STATUS:(\d+)\s*$')
        if (-not $statusMatch.Success) {
            throw "Gateway call $Method $Path returned no HTTP status. Is the stack running?"
        }
        $status = [int]$statusMatch.Groups[1].Value
        $body = ($raw -replace "`nHTTP_STATUS:\d+\s*$", '').Trim()

        if ($status -eq 0) {
            throw "Gateway is not reachable at $BaseUrl. Start the stack with scripts\dev-start.ps1."
        }
        if ($status -eq 429 -and $attempt -lt 3) {
            Start-Sleep -Seconds 1
            continue
        }
        return [pscustomobject]@{ Status = $status; Body = $body }
    }
}

function Assert-Status {
    param($Response, [int[]]$Expected, [string]$What)
    if ($Expected -notcontains $Response.Status) {
        throw "$What failed with HTTP $($Response.Status): $($Response.Body)"
    }
}

function Connect-Instructor {
    param([string]$TenantSlug, [string]$Email)

    $payload = @{
        tenantSlug = $TenantSlug
        email = $Email
        password = $demoPassword
    } | ConvertTo-Json -Compress

    $response = Invoke-Gateway -Method 'POST' -Path '/api/auth/login' -JsonBody $payload
    Assert-Status $response @(200) "Login for $Email"
    $json = $response.Body | ConvertFrom-Json
    if (-not $json.accessToken) {
        throw "Login for $Email returned no access token."
    }
    return $json.accessToken
}

$rows = New-Object System.Collections.ArrayList
$createdCount = 0
$skippedCount = 0

try {
    Test-DemoData

    foreach ($tenant in $data.tenants) {
        $instructorEmail = $tenant.instructorEmail
        Write-Host ("Tenant {0}: logging in as {1}" -f $tenant.slug, $instructorEmail)
        $token = Connect-Instructor -TenantSlug $tenant.slug -Email $instructorEmail

        $meResponse = Invoke-Gateway -Method 'GET' -Path '/api/auth/me' -Token $token
        Assert-Status $meResponse @(200) 'Reading the instructor profile'
        $instructorId = ($meResponse.Body | ConvertFrom-Json).id

        # The listing returns this instructor's own courses in every status plus other
        # instructors' published courses, so filter by instructorId before deciding to skip.
        $listResponse = Invoke-Gateway -Method 'GET' -Path '/api/courses?page=0&size=50' -Token $token
        Assert-Status $listResponse @(200) 'Listing existing courses'
        $existing = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($item in ($listResponse.Body | ConvertFrom-Json).items) {
            if ($item.instructorId -eq $instructorId) {
                [void]$existing.Add([string]$item.title)
            }
        }

        foreach ($course in $tenant.courses) {
            if ($existing.Contains([string]$course.title)) {
                [void]$rows.Add([pscustomobject]@{
                    Tenant  = $tenant.slug
                    Course  = $course.title
                    Target  = $course.status
                    Modules = '-'
                    Lessons = '-'
                    Result  = 'skipped (exists)'
                })
                $skippedCount++
                continue
            }

            $coursePayload = @{
                title       = $course.title
                description = $course.description
                price       = $course.price
                currency    = $course.currency
            } | ConvertTo-Json -Compress
            $courseResponse = Invoke-Gateway -Method 'POST' -Path '/api/courses' `
                -Token $token -JsonBody $coursePayload
            Assert-Status $courseResponse @(201) "Creating course '$($course.title)'"
            $courseId = ($courseResponse.Body | ConvertFrom-Json).id

            $moduleCount = 0
            $lessonCount = 0
            foreach ($module in $course.modules) {
                $modulePayload = @{ title = $module.title } | ConvertTo-Json -Compress
                $moduleResponse = Invoke-Gateway -Method 'POST' -Path "/api/courses/$courseId/modules" `
                    -Token $token -JsonBody $modulePayload
                Assert-Status $moduleResponse @(201) "Creating module '$($module.title)'"
                $moduleId = ($moduleResponse.Body | ConvertFrom-Json).id
                $moduleCount++

                foreach ($lesson in $module.lessons) {
                    $lessonPayload = @{
                        title           = $lesson.title
                        durationSeconds = $lesson.durationSeconds
                        freePreview     = [bool]$lesson.freePreview
                    } | ConvertTo-Json -Compress
                    $lessonResponse = Invoke-Gateway -Method 'POST' `
                        -Path "/api/modules/$moduleId/lessons" -Token $token -JsonBody $lessonPayload
                    Assert-Status $lessonResponse @(201) "Creating lesson '$($lesson.title)'"
                    $lessonCount++
                }
            }

            if ($course.status -eq 'PUBLISHED') {
                $publishResponse = Invoke-Gateway -Method 'PATCH' `
                    -Path "/api/courses/$courseId/publish" -Token $token
                Assert-Status $publishResponse @(200) "Publishing course '$($course.title)'"
            }

            [void]$existing.Add([string]$course.title)
            [void]$rows.Add([pscustomobject]@{
                Tenant  = $tenant.slug
                Course  = $course.title
                Target  = $course.status
                Modules = $moduleCount
                Lessons = $lessonCount
                Result  = 'created'
            })
            $createdCount++
        }
    }
} catch {
    if ($rows.Count -gt 0) {
        Write-Host ''
        Write-Host ($rows | Format-Table -AutoSize | Out-String -Width 200)
    }
    Write-Host ("ERROR: {0}" -f $_.Exception.Message)
    exit 1
}

Write-Host ''
Write-Host ($rows | Format-Table -AutoSize | Out-String -Width 200)
Write-Host ("Created {0} course(s), skipped {1} existing course(s)." -f $createdCount, $skippedCount)
Write-Host 'Demo courses are available through http://localhost:8080/api/courses.'
