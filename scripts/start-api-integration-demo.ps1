$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$settingsPath = Join-Path $backendDirectory '.env.integration.local'
$mockScript = Join-Path $PSScriptRoot 'mock-external-api.ps1'
$settings = [ordered]@{}

& git -C $projectRoot check-ignore -q -- 'backend/.env.integration.local'
if ($LASTEXITCODE -ne 0) {
    throw 'Refusing to store demonstration keys because backend/.env.integration.local is not Git-ignored.'
}

if (Test-Path -LiteralPath $settingsPath -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $settingsPath) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) {
            continue
        }
        $separator = $text.IndexOf('=')
        if ($separator -lt 1) {
            throw 'The local integration configuration contains a malformed entry.'
        }
        $name = $text.Substring(0, $separator).Trim()
        if ($settings.Contains($name)) {
            throw 'The local integration configuration contains a duplicate variable.'
        }
        $settings[$name] = $text.Substring($separator + 1)
    }
}

function New-DemoKey {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Protect-LocalSettings([string]$Path) {
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $systemSid = 'S-1-5-18'
    & icacls.exe $Path /inheritance:r | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not disable inherited DACL entries for the local integration settings.'
    }

    $acl = Get-Acl -LiteralPath $Path
    $principals = @{}
    foreach ($rule in $acl.Access) {
        try {
            $sid = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        }
        catch {
            throw 'Could not identify an existing DACL principal; local settings permissions were not fully secured.'
        }
        $principals[$sid] = $true
    }

    foreach ($sid in @($principals.Keys)) {
        if ($sid -eq $currentSid -or $sid -eq $systemSid) {
            continue
        }
        & icacls.exe $Path /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not remove an unapproved DACL principal from the local integration settings.'
        }
    }

    foreach ($sid in @($currentSid, $systemSid)) {
        & icacls.exe $Path /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not replace the DACL for the local integration settings.'
        }
    }
    & icacls.exe $Path /grant:r "*$currentSid`:(F)" /grant:r "*$systemSid`:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not grant protected DACL access to the current user and SYSTEM.'
    }

    $verifiedAcl = Get-Acl -LiteralPath $Path
    $verifiedRules = @($verifiedAcl.Access)
    $verifiedSids = @($verifiedRules | ForEach-Object {
        $_.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
    } | Sort-Object -Unique)
    if (-not $verifiedAcl.AreAccessRulesProtected -or $verifiedRules.Count -ne 2 -or
            $verifiedSids.Count -ne 2 -or $verifiedSids -notcontains $currentSid -or
            $verifiedSids -notcontains $systemSid -or
            @($verifiedRules | Where-Object {
                $_.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow -or
                $_.FileSystemRights -ne [System.Security.AccessControl.FileSystemRights]::FullControl
            }).Count -ne 0) {
        throw 'The local integration settings DACL did not match the required user-and-SYSTEM-only policy.'
    }
}

$changed = $false
foreach ($keyName in @('TRACK2_INTEGRATION_API_KEY', 'DEMO_EXTERNAL_API_KEY')) {
    if ([string]::IsNullOrWhiteSpace($settings[$keyName])) {
        $settings[$keyName] = New-DemoKey
        $changed = $true
    }
}

$demoEndpoints = @(
    'http://127.0.0.1:8091/v1/demo',
    'http://127.0.0.1:8091/v1/track2-summary'
)
$configuredEndpoints = @()
if (-not [string]::IsNullOrWhiteSpace($settings['EXTERNAL_API_ALLOWED_ENDPOINTS'])) {
    $configuredEndpoints = @(
        $settings['EXTERNAL_API_ALLOWED_ENDPOINTS'].Split(',') |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}
foreach ($endpoint in $demoEndpoints) {
    if ($configuredEndpoints -cnotcontains $endpoint) {
        $configuredEndpoints += $endpoint
        $changed = $true
    }
}
$settings['EXTERNAL_API_ALLOWED_ENDPOINTS'] = $configuredEndpoints -join ','

if ($changed -or -not (Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
    $names = @($settings.Keys | Sort-Object)
    $lines = @($names | ForEach-Object { '{0}={1}' -f $_, $settings[$_] })
    [System.IO.File]::WriteAllLines($settingsPath, $lines, [System.Text.UTF8Encoding]::new($false))
}
Protect-LocalSettings $settingsPath

$tcp = New-Object System.Net.Sockets.TcpClient
try {
    $connect = $tcp.BeginConnect('127.0.0.1', 8091, $null, $null)
    if ($connect.AsyncWaitHandle.WaitOne(300)) {
        try {
            $tcp.EndConnect($connect)
            Write-Output 'The mock API is already listening on 127.0.0.1:8091.'
        }
        catch {
            $process = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') `
                -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$mockScript`"") `
                -PassThru -WindowStyle Normal
            Start-Sleep -Seconds 2
            if ($process.HasExited) {
                throw 'The local mock API process exited before becoming ready.'
            }
            $probe = New-Object System.Net.Sockets.TcpClient
            try {
                $probe.Connect('127.0.0.1', 8091)
            }
            finally {
                $probe.Dispose()
            }
            Write-Output 'Started the demonstration mock API on 127.0.0.1:8091.'
        }
    }
    else {
        $process = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$mockScript`"") `
            -PassThru -WindowStyle Normal
        Start-Sleep -Seconds 2
        if ($process.HasExited) {
            throw 'The local mock API process exited before becoming ready.'
        }
        $probe = New-Object System.Net.Sockets.TcpClient
        try {
            $probe.Connect('127.0.0.1', 8091)
        }
        finally {
            $probe.Dispose()
        }
        Write-Output 'Started the demonstration mock API on 127.0.0.1:8091.'
    }
}
finally {
    $tcp.Dispose()
}

Write-Output 'The local API-key settings are saved in the Git-ignored backend/.env.integration.local.'
Write-Output 'The demo key is not copied or displayed. Use scripts/manage-api-integration-keys.ps1 -Mode CopyExternalKey only when needed.'
Write-Output 'Restart Spring Boot with .\scripts\start-backend.ps1 to load the provider key and endpoint allowlist.'
Write-Output 'The mock listens on loopback only. Close its PowerShell window to stop it.'
