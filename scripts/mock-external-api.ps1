$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsPath = Join-Path $projectRoot 'backend\.env.integration.local'
if (-not (Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
    throw 'Run scripts/start-api-integration-demo.ps1 first to create local demo settings.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $settingsPath) {
    $text = $line.Trim()
    if (-not $text -or $text.StartsWith('#')) {
        continue
    }
    $separator = $text.IndexOf('=')
    if ($separator -lt 1) {
        throw 'The local integration configuration contains a malformed entry.'
    }
    $settings[$text.Substring(0, $separator).Trim()] = $text.Substring($separator + 1)
}
$externalApiKey = $settings['DEMO_EXTERNAL_API_KEY']
$track2ApiKey = $settings['TRACK2_INTEGRATION_API_KEY']
if ([string]::IsNullOrWhiteSpace($externalApiKey) -or [string]::IsNullOrWhiteSpace($track2ApiKey)) {
    throw 'The local demonstration keys are not configured.'
}

function Test-ApiKey([string]$Supplied, [string]$Expected) {
    if ([string]::IsNullOrEmpty($Supplied) -or $Supplied.Length -gt 512) {
        return $false
    }
    $suppliedBytes = [System.Text.Encoding]::UTF8.GetBytes($Supplied)
    $expectedBytes = [System.Text.Encoding]::UTF8.GetBytes($Expected)
    if ($suppliedBytes.Length -ne $expectedBytes.Length) {
        return $false
    }
    $difference = 0
    for ($index = 0; $index -lt $suppliedBytes.Length; $index++) {
        $difference = $difference -bor ($suppliedBytes[$index] -bxor $expectedBytes[$index])
    }
    return $difference -eq 0
}

function Send-Json($Context, [int]$StatusCode, [string]$Json) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
    $Context.Response.StatusCode = $StatusCode
    $Context.Response.ContentType = 'application/json; charset=utf-8'
    $Context.Response.ContentLength64 = $bytes.Length
    $Context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8091/')
$listener.Start()
Write-Output 'Demonstration mock API listening on http://127.0.0.1:8091/ (loopback only).'

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        try {
            if ($context.Request.HttpMethod -cne 'GET') {
                Send-Json $context 405 '{"error":"Only GET is supported."}'
                continue
            }
            if (-not (Test-ApiKey $context.Request.Headers['X-API-Key'] $externalApiKey)) {
                Send-Json $context 401 '{"error":"A valid X-API-Key is required.","source":"DEMONSTRATION_ONLY"}'
                continue
            }

            switch ($context.Request.Url.AbsolutePath) {
                '/v1/demo' {
                    Send-Json $context 200 (@{
                        source = 'DEMONSTRATION_ONLY'
                        description = 'Sample response; not real integrated records.'
                        items = @(
                            @{ code = 'DEMO-01'; label = 'Sample partner record' },
                            @{ code = 'DEMO-02'; label = 'Sample service status' }
                        )
                    } | ConvertTo-Json -Depth 5 -Compress)
                }
                '/v1/track2-summary' {
                    try {
                        $track2 = Invoke-RestMethod -Method Get `
                            -Uri 'http://127.0.0.1:8080/api/integration/v1/summary' `
                            -Headers @{ 'X-API-Key' = $track2ApiKey } `
                            -TimeoutSec 10
                        Send-Json $context 200 (@{
                            source = 'DEMONSTRATION_ONLY'
                            description = 'Summary retrieved from the Track 2 shared API-key endpoint.'
                            track2 = $track2
                        } | ConvertTo-Json -Depth 8 -Compress)
                    }
                    catch {
                        Send-Json $context 502 '{"error":"Track 2 could not be reached or rejected the configured integration key.","source":"DEMONSTRATION_ONLY"}'
                    }
                }
                default {
                    Send-Json $context 404 '{"error":"Unknown demonstration endpoint.","source":"DEMONSTRATION_ONLY"}'
                }
            }
        }
        finally {
            $context.Response.Close()
        }
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
