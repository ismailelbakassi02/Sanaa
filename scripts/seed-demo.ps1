# Seed the marketplace demo through the gateway.
# Compatible with PowerShell 5.1. This file is intentionally ASCII-only.
# The French demo content lives as UTF-8 in demo-courses.json and is read with -Encoding UTF8.

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

# A missing "reviews" key converts to $null, and @($null) is a one-element array in Windows
# PowerShell. Filter the empty entry so a course without reviews really has none.
function Get-CourseReviews($Course) {
    return @($Course.reviews | Where-Object { $_ -and $_.learnerEmail })
}

function Test-DemoData {
    if (-not $data.publishers -or $data.publishers.Count -eq 0) {
        throw 'The demo file must declare at least one publisher.'
    }
    $published = 0
    $drafts = 0
    $reviewTotal = 0
    $freePublishedWithoutReview = 0
    foreach ($publisher in $data.publishers) {
        if (-not $publisher.slug -or $publisher.courses.Count -eq 0) {
            throw 'Every publisher needs a slug and at least one course.'
        }
        foreach ($course in $publisher.courses) {
            if (-not $course.instructorEmail -or -not $course.title -or -not $course.summary -or
                -not $course.description) {
                throw "Course '$($course.title)' is missing an instructorEmail, title, summary or description."
            }
            if (-not $course.categorySlug) {
                throw "Course '$($course.title)' needs a categorySlug."
            }
            if ($course.currency -ne 'MAD') {
                throw "Course '$($course.title)' must use MAD."
            }
            if ($course.price -lt 0) {
                throw "Course '$($course.title)' has a negative price."
            }
            if (-not $course.skills -or $course.skills.Count -eq 0) {
                throw "Course '$($course.title)' needs at least one skill: the publish rule requires it."
            }
            if ($course.tags.Count -gt 10) {
                throw "Course '$($course.title)' has more than ten tags."
            }
            if ($course.status -eq 'PUBLISHED') {
                $published++
            } elseif ($course.status -eq 'DRAFT') {
                $drafts++
            } else {
                throw "Course '$($course.title)' has an unknown status '$($course.status)'."
            }
            if ($course.modules.Count -eq 0) {
                throw "Course '$($course.title)' must declare at least one module."
            }
            $freeLessons = 0
            foreach ($module in $course.modules) {
                if (-not $module.title) {
                    throw "A module of course '$($course.title)' has no title."
                }
                if ($module.lessons.Count -eq 0) {
                    throw "Module '$($module.title)' must declare at least one lesson."
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
            if ($course.status -eq 'PUBLISHED' -and $freeLessons -eq 0) {
                throw "Published course '$($course.title)' needs one free-preview lesson."
            }
            $reviews = Get-CourseReviews $course
            if ($reviews.Count -gt 0) {
                if ($course.price -ne 0) {
                    throw "Course '$($course.title)' has reviews but is not free. Only free enrollment can be seeded without a payment."
                }
                if ($course.status -ne 'PUBLISHED') {
                    throw "Course '$($course.title)' has reviews but is not published."
                }
                $reviewTotal += $reviews.Count
            } else {
                if ($course.status -eq 'PUBLISHED' -and $course.price -eq 0) {
                    $freePublishedWithoutReview++
                }
            }
        }
    }
    if ($published -eq 0 -or $drafts -eq 0) {
        throw 'The demo needs at least one published course and one draft course.'
    }
    # The catalogue must show a real rating sort and an empty one: at least ten reviews, and at
    # least one free published course that no learner has reviewed.
    if ($reviewTotal -lt 10) {
        throw "The demo needs at least ten reviews, found $reviewTotal."
    }
    if ($freePublishedWithoutReview -eq 0) {
        throw 'The demo needs at least one free published course without reviews.'
    }
}

function Invoke-Gateway {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$JsonBody
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
        if ($null -ne $JsonBody) {
            # PowerShell strips embedded quotes when an inline JSON argument reaches a native
            # command, and Out-File adds a BOM. Write UTF-8 without BOM to a file instead.
            $json = $JsonBody | ConvertTo-Json -Depth 10 -Compress
            $tempFile = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllText(
                $tempFile, $json, (New-Object System.Text.UTF8Encoding -ArgumentList $false))
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

function Connect-User {
    param([string]$Email)
    if (-not $script:tokens.ContainsKey($Email)) {
        $payload = @{
            email    = $Email
            password = $demoPassword
        }
        $response = Invoke-Gateway -Method 'POST' -Path '/api/auth/login' -JsonBody $payload
        Assert-Status $response @(200) "Login for $Email"
        $json = $response.Body | ConvertFrom-Json
        if (-not $json.accessToken) {
            throw "Login for $Email returned no access token."
        }
        $script:tokens[$Email] = $json.accessToken
    }
    return $script:tokens[$Email]
}

$script:tokens = @{}
# Course ids by "publisher|title", filled while creating or skipping, used by the review phase.
$courseIds = @{}
$rows = New-Object System.Collections.ArrayList
$createdCount = 0
$skippedCount = 0

try {
    Test-DemoData

    $categoryResponse = Invoke-Gateway -Method 'GET' -Path '/api/public/categories'
    Assert-Status $categoryResponse @(200) 'Reading the public categories'
    $categoryIds = @{}
    foreach ($category in ($categoryResponse.Body | ConvertFrom-Json)) {
        $categoryIds[$category.slug] = $category.id
    }

    foreach ($publisher in $data.publishers) {
        foreach ($course in $publisher.courses) {
            if (-not $categoryIds.ContainsKey($course.categorySlug)) {
                throw "Unknown category slug '$($course.categorySlug)' for course '$($course.title)'."
            }

            $token = Connect-User -Email $course.instructorEmail
            $listResponse = Invoke-Gateway -Method 'GET' -Path '/api/studio/courses?page=0&size=50' -Token $token
            Assert-Status $listResponse @(200) 'Listing studio courses'
            $existing = @{}
            foreach ($item in ($listResponse.Body | ConvertFrom-Json).items) {
                $existing[[string]$item.title] = $item.id
            }

            if ($existing.ContainsKey([string]$course.title)) {
                $courseIds["$($publisher.slug)|$($course.title)"] = $existing[[string]$course.title]
                [void]$rows.Add([pscustomobject]@{
                    Publisher = $publisher.slug
                    Course    = $course.title
                    Status    = $course.status
                    Modules   = '-'
                    Lessons   = '-'
                    Reviews   = (Get-CourseReviews $course).Count
                    Result    = 'skipped (exists)'
                })
                $skippedCount++
                continue
            }

            $coursePayload = @{
                title        = $course.title
                summary      = $course.summary
                description  = $course.description
                categoryId   = $categoryIds[$course.categorySlug]
                level        = $course.level
                language     = $course.language
                skills       = @($course.skills)
                requirements = @($course.requirements)
                price        = $course.price
                currency     = $course.currency
                tags         = @($course.tags)
            }
            $courseResponse = Invoke-Gateway -Method 'POST' -Path '/api/studio/courses' `
                -Token $token -JsonBody $coursePayload
            Assert-Status $courseResponse @(201) "Creating course '$($course.title)'"
            $courseId = ($courseResponse.Body | ConvertFrom-Json).id
            $courseIds["$($publisher.slug)|$($course.title)"] = $courseId

            $moduleCount = 0
            $lessonCount = 0
            foreach ($module in $course.modules) {
                $moduleResponse = Invoke-Gateway -Method 'POST' `
                    -Path "/api/studio/courses/$courseId/modules" `
                    -Token $token -JsonBody @{ title = $module.title }
                Assert-Status $moduleResponse @(201) "Creating module '$($module.title)'"
                $moduleId = ($moduleResponse.Body | ConvertFrom-Json).id
                $moduleCount++

                foreach ($lesson in $module.lessons) {
                    $lessonPayload = @{
                        title           = $lesson.title
                        description     = $lesson.description
                        durationSeconds = $lesson.durationSeconds
                        freePreview     = [bool]$lesson.freePreview
                    }
                    $lessonResponse = Invoke-Gateway -Method 'POST' `
                        -Path "/api/studio/modules/$moduleId/lessons" `
                        -Token $token -JsonBody $lessonPayload
                    Assert-Status $lessonResponse @(201) "Creating lesson '$($lesson.title)'"
                    $lessonCount++
                }
            }

            if ($course.discount) {
                $endsAt = (Get-Date).ToUniversalTime().AddDays(
                    [int]$course.discount.endsInDays).ToString('yyyy-MM-ddTHH:mm:ssZ')
                $discountResponse = Invoke-Gateway -Method 'PUT' `
                    -Path "/api/studio/courses/$courseId/discount" -Token $token `
                    -JsonBody @{ price = $course.discount.price; endsAt = $endsAt }
                Assert-Status $discountResponse @(200) "Adding the discount of '$($course.title)'"
            }

            if ($course.status -eq 'PUBLISHED') {
                $publishResponse = Invoke-Gateway -Method 'POST' `
                    -Path "/api/studio/courses/$courseId/publish" -Token $token
                Assert-Status $publishResponse @(200) "Publishing course '$($course.title)'"
            }

            [void]$rows.Add([pscustomobject]@{
                Publisher = $publisher.slug
                Course    = $course.title
                Status    = $course.status
                Modules   = $moduleCount
                Lessons   = $lessonCount
                Reviews   = (Get-CourseReviews $course).Count
                Result    = 'created'
            })
            $createdCount++
        }
    }

    # Second phase: enroll the demo learners and post the reviews. It runs on every seed, because
    # the first phase skips existing courses. Both endpoints are idempotent: enrollment answers 201
    # or 409 when the learner already has access, and a review is an upsert that returns 201, so a
    # rerun refreshes the ratings without duplicating anything.
    $seededReviews = 0
    $seededReplies = 0
    foreach ($publisher in $data.publishers) {
        foreach ($course in $publisher.courses) {
            $reviews = Get-CourseReviews $course
            if ($reviews.Count -eq 0) {
                continue
            }
            $courseId = $courseIds["$($publisher.slug)|$($course.title)"]
            if (-not $courseId) {
                throw "Course '$($course.title)' was not created or found, cannot seed its reviews."
            }
            foreach ($review in $reviews) {
                $learnerToken = Connect-User -Email $review.learnerEmail
                $enrollResponse = Invoke-Gateway -Method 'POST' `
                    -Path "/api/courses/$courseId/enroll" -Token $learnerToken
                Assert-Status $enrollResponse @(201, 409) "Enrolling $($review.learnerEmail)"
                $reviewResponse = Invoke-Gateway -Method 'POST' `
                    -Path "/api/courses/$courseId/reviews" -Token $learnerToken `
                    -JsonBody @{ rating = $review.rating; comment = $review.comment }
                Assert-Status $reviewResponse @(201) "Reviewing '$($course.title)'"
                $seededReviews++

                # The publisher reply is optional and accepted once. A rerun answers 409 because the
                # reply already exists, which is the expected idempotent outcome, not an error.
                if ($review.publisherReply) {
                    $reviewId = ($reviewResponse.Body | ConvertFrom-Json).id
                    if (-not $reviewId) {
                        throw "Review on '$($course.title)' returned no id, cannot add its reply."
                    }
                    $adminToken = Connect-User -Email ("admin@{0}.local" -f $publisher.slug)
                    $replyResponse = Invoke-Gateway -Method 'POST' `
                        -Path "/api/studio/reviews/$reviewId/reply" -Token $adminToken `
                        -JsonBody @{ reply = $review.publisherReply }
                    Assert-Status $replyResponse @(200, 204, 409) "Replying to '$($course.title)'"
                    $seededReplies++
                }
            }
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
Write-Host ("Seeded {0} review(s) and {1} publisher repl(y/ies) from the demo data." -f $seededReviews, $seededReplies)
Write-Host 'Catalogue: http://localhost:8080/api/public/courses'
Write-Host 'Studio   : http://localhost:8080/api/studio/courses'
