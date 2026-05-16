param(
    [string] $BaseUrl = "http://localhost:8080",
    [int] $SessionCount = 5,
    [int] $MessagesPerSession = 20,
    [string] $OutputPath = "build/load-test-result.json"
)

$ErrorActionPreference = "Stop"

function New-JsonBody {
    param([hashtable] $Value)
    return ($Value | ConvertTo-Json -Depth 10 -Compress)
}

function Invoke-MeasuredJson {
    param(
        [string] $Method,
        [string] $Uri,
        [string] $Body = $null
    )

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if ([string]::IsNullOrEmpty($Body)) {
            $response = Invoke-RestMethod -Method $Method -Uri $Uri
        }
        else {
            $response = Invoke-RestMethod -Method $Method -Uri $Uri -ContentType "application/json" -Body $Body
        }
        $watch.Stop()
        return [pscustomobject]@{
            ok = $true
            latencyMs = $watch.Elapsed.TotalMilliseconds
            response = $response
            error = $null
        }
    }
    catch {
        $watch.Stop()
        return [pscustomobject]@{
            ok = $false
            latencyMs = $watch.Elapsed.TotalMilliseconds
            response = $null
            error = $_.Exception.Message
        }
    }
}

function Get-Percentile {
    param(
        [double[]] $Values,
        [double] $Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }
    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return [Math]::Round([double] $sorted[$index], 2)
}

$startedAt = Get-Date
$runWatch = [System.Diagnostics.Stopwatch]::StartNew()
$results = New-Object System.Collections.Generic.List[object]
$sessionIds = New-Object System.Collections.Generic.List[string]
$errors = New-Object System.Collections.Generic.List[object]

Write-Host "Load test started: $BaseUrl"
Write-Host "Sessions=$SessionCount, MessagesPerSession=$MessagesPerSession"

for ($sessionIndex = 1; $sessionIndex -le $SessionCount; $sessionIndex++) {
    $create = Invoke-MeasuredJson -Method "POST" -Uri "$BaseUrl/sessions" -Body "{}"
    $results.Add([pscustomobject]@{ step = "create-session"; latencyMs = $create.latencyMs; ok = $create.ok })
    if (-not $create.ok) {
        $errors.Add([pscustomobject]@{ step = "create-session"; error = $create.error })
        continue
    }

    $sessionId = $create.response.sessionId
    $sessionIds.Add($sessionId)
    $userId = "load-user-$sessionIndex"

    $joinBody = New-JsonBody @{
        userId = $userId
        displayName = "Load User $sessionIndex"
        clientEventId = "load-join-$sessionIndex"
    }
    $join = Invoke-MeasuredJson -Method "POST" -Uri "$BaseUrl/sessions/$sessionId/join" -Body $joinBody
    $results.Add([pscustomobject]@{ step = "join"; latencyMs = $join.latencyMs; ok = $join.ok })
    if (-not $join.ok) {
        $errors.Add([pscustomobject]@{ step = "join"; sessionId = $sessionId; error = $join.error })
        continue
    }

    for ($messageIndex = 1; $messageIndex -le $MessagesPerSession; $messageIndex++) {
        $clientEventId = "load-message-$sessionIndex-$messageIndex"
        $messageBody = New-JsonBody @{
            type = "MESSAGE_SENT"
            senderId = $userId
            clientEventId = $clientEventId
            payload = @{
                messageId = "load-msg-$sessionIndex-$messageIndex"
                content = "load message $sessionIndex-$messageIndex"
            }
        }
        $message = Invoke-MeasuredJson -Method "POST" -Uri "$BaseUrl/sessions/$sessionId/events" -Body $messageBody
        $results.Add([pscustomobject]@{ step = "message"; latencyMs = $message.latencyMs; ok = $message.ok })
        if (-not $message.ok) {
            $errors.Add([pscustomobject]@{ step = "message"; sessionId = $sessionId; error = $message.error })
        }

        if ($messageIndex -eq 1) {
            $duplicate = Invoke-MeasuredJson -Method "POST" -Uri "$BaseUrl/sessions/$sessionId/events" -Body $messageBody
            $isDuplicate = $duplicate.ok -and $duplicate.response.duplicate
            $results.Add([pscustomobject]@{ step = "duplicate-message"; latencyMs = $duplicate.latencyMs; ok = $isDuplicate })
            if (-not $isDuplicate) {
                $errors.Add([pscustomobject]@{ step = "duplicate-message"; sessionId = $sessionId; error = $duplicate.error })
            }
        }
    }

    $snapshot = Invoke-MeasuredJson -Method "POST" -Uri "$BaseUrl/sessions/$sessionId/snapshots" -Body "{}"
    $results.Add([pscustomobject]@{ step = "snapshot"; latencyMs = $snapshot.latencyMs; ok = $snapshot.ok })
    if (-not $snapshot.ok) {
        $errors.Add([pscustomobject]@{ step = "snapshot"; sessionId = $sessionId; error = $snapshot.error })
    }

    $timelineAt = [uri]::EscapeDataString("2026-12-31T23:59:59+09:00")
    $timeline = Invoke-MeasuredJson -Method "GET" -Uri "$BaseUrl/sessions/$sessionId/timeline?at=$timelineAt&messageLimit=100"
    $timelineOk = $timeline.ok -and $timeline.response.restoredFromSnapshot
    $results.Add([pscustomobject]@{ step = "timeline"; latencyMs = $timeline.latencyMs; ok = $timelineOk })
    if (-not $timelineOk) {
        $errors.Add([pscustomobject]@{ step = "timeline"; sessionId = $sessionId; error = $timeline.error })
    }
}

$runWatch.Stop()
$latencies = @($results | ForEach-Object { [double] $_.latencyMs })
$successful = @($results | Where-Object { $_.ok })
$failed = @($results | Where-Object { -not $_.ok })

$summary = [pscustomobject]@{
    startedAt = $startedAt.ToString("o")
    baseUrl = $BaseUrl
    sessionCount = $SessionCount
    messagesPerSession = $MessagesPerSession
    createdSessionIds = $sessionIds
    totalRequests = $results.Count
    successfulRequests = $successful.Count
    failedRequests = $failed.Count
    elapsedMs = [Math]::Round($runWatch.Elapsed.TotalMilliseconds, 2)
    requestsPerSecond = if ($runWatch.Elapsed.TotalSeconds -gt 0) { [Math]::Round($results.Count / $runWatch.Elapsed.TotalSeconds, 2) } else { 0 }
    latencyMs = [pscustomobject]@{
        min = Get-Percentile -Values $latencies -Percentile 0
        p50 = Get-Percentile -Values $latencies -Percentile 50
        p95 = Get-Percentile -Values $latencies -Percentile 95
        max = Get-Percentile -Values $latencies -Percentile 100
    }
    byStep = $results |
        Group-Object step |
        ForEach-Object {
            $stepLatencies = @($_.Group | ForEach-Object { [double] $_.latencyMs })
            [pscustomobject]@{
                step = $_.Name
                count = $_.Count
                failed = @($_.Group | Where-Object { -not $_.ok }).Count
                p95LatencyMs = Get-Percentile -Values $stepLatencies -Percentile 95
            }
        }
    errors = $errors
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$summary | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path $OutputPath
$summary | ConvertTo-Json -Depth 10
