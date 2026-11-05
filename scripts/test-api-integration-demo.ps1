$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsPath = Join-Path $projectRoot 'backend\.env.integration.local'
if (-not (Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
    throw 'Run scripts/start-api-integration-demo.ps1 first.'
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

function Invoke-DemoGet([string]$Uri, [string]$ApiKey) {
    $parameters = @{ Uri = $Uri; Method = 'Get'; UseBasicParsing = $true; TimeoutSec = 10 }
    if ($null -ne $ApiKey) {
        $parameters.Headers = @{ 'X-API-Key' = $ApiKey }
    }
    try {
        $response = Invoke-WebRequest @parameters
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Content = $response.Content
        }
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            throw 'The demonstration API request failed before receiving an HTTP response.'
        }
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Content = ''
        }
    }
}

function Assert-Status([string]$Name, $Response, [int]$Expected) {
    if ($Response.Status -ne $Expected) {
        throw "$Name failed: expected HTTP $Expected, received HTTP $($Response.Status)."
    }
    Write-Output "$Name`: PASS (HTTP $Expected)"
}

$mockUrl = 'http://127.0.0.1:8091/v1/demo'
$track2Url = 'http://127.0.0.1:8080/api/integration/v1/summary'
$mockKey = $settings['DEMO_EXTERNAL_API_KEY']
$track2Key = $settings['TRACK2_INTEGRATION_API_KEY']
if ([string]::IsNullOrWhiteSpace($mockKey) -or [string]::IsNullOrWhiteSpace($track2Key)) {
    throw 'The local demonstration keys are not configured.'
}

$wrongKey = [guid]::NewGuid().ToString('N')
$validMock = Invoke-DemoGet $mockUrl $mockKey
Assert-Status 'Mock API correct key' $validMock 200
if ($validMock.Content -notmatch 'DEMONSTRATION_ONLY') {
    throw 'The mock response is missing its demonstration-data label.'
}
Write-Output 'Mock response is labeled demonstration data: PASS'
Assert-Status 'Mock API wrong key' (Invoke-DemoGet $mockUrl $wrongKey) 401
Assert-Status 'Mock API missing key' (Invoke-DemoGet $mockUrl $null) 401

Assert-Status 'Track 2 correct key' (Invoke-DemoGet $track2Url $track2Key) 200
Assert-Status 'Track 2 wrong key' (Invoke-DemoGet $track2Url $wrongKey) 401
Assert-Status 'Track 2 missing key' (Invoke-DemoGet $track2Url $null) 401

$reverseResponse = Invoke-DemoGet 'http://127.0.0.1:8091/v1/track2-summary' $mockKey
Assert-Status 'Mock retrieves Track 2 summary' $reverseResponse 200
if ($reverseResponse.Content -notmatch 'Track 2 Department System') {
    throw 'The mock did not receive the expected non-personal Track 2 summary.'
}
Write-Output 'Mock-to-Track-2 JSON exchange: PASS'
Write-Output 'Track 2-to-mock retrieval is demonstrated from the Admin External API page.'
