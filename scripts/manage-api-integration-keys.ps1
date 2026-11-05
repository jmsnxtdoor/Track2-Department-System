[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Activate', 'CopyExternalKey', 'SecureLocalConfig')]
    [string]$Mode = 'Prepare'
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$settingsPath = Join-Path $backendDirectory '.env.integration.local'
$pendingPath = Join-Path $backendDirectory '.env.integration.pending.local'
$backupPath = Join-Path $backendDirectory '.env.integration.previous.local'
$keyNames = @('DEMO_EXTERNAL_API_KEY', 'TRACK2_INTEGRATION_API_KEY')

function Assert-GitIgnored([string]$Path) {
    $relativePath = $Path.Substring($projectRoot.Length).TrimStart([char[]]@('\', '/'))
    & git -C $projectRoot check-ignore -q -- $relativePath
    if ($LASTEXITCODE -ne 0) {
        throw 'Refusing to store integration keys because the local configuration is not Git-ignored.'
    }
}

function Read-LocalSettings([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'Local integration settings are missing. Run the demo setup script first.'
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) {
            continue
        }
        $separator = $text.IndexOf('=')
        if ($separator -lt 1) {
            throw 'The local integration configuration contains a malformed entry.'
        }
        $name = $text.Substring(0, $separator).Trim()
        if ($values.ContainsKey($name)) {
            throw 'The local integration configuration contains a duplicate variable.'
        }
        $values[$name] = $text.Substring($separator + 1)
    }
    return $values
}

function Assert-KeysPresent($Values) {
    foreach ($name in $keyNames) {
        if ([string]::IsNullOrWhiteSpace([string]$Values[$name])) {
            throw 'Both local demonstration keys must be configured before rotation.'
        }
    }
}

function New-SecureApiKey {
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

function Protect-LocalFile([string]$Path) {
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

function Write-PendingSettings($Values) {
    if (Test-Path -LiteralPath $pendingPath) {
        throw 'A pending key rotation already exists. Activate it or remove it manually before preparing another.'
    }
    $temporaryPath = Join-Path $backendDirectory '.env.integration.pending.local.tmp'
    if (Test-Path -LiteralPath $temporaryPath) {
        throw 'A temporary integration settings file already exists; inspect it before continuing.'
    }

    try {
        $names = @($Values.Keys | Sort-Object)
        $lines = @($names | ForEach-Object { '{0}={1}' -f $_, $Values[$_] })
        [System.IO.File]::WriteAllLines($temporaryPath, $lines, [System.Text.UTF8Encoding]::new($false))
        Protect-LocalFile $temporaryPath
        [System.IO.File]::Move($temporaryPath, $pendingPath)
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Assert-ApiListenersStopped {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080, 8091 -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        throw 'Rotation activation is blocked while port 8080 or 8091 is listening. Stop the services manually during an approved window, then retry.'
    }
}

function Assert-NoStaleSettings($Current, $Pending) {
    $allNames = @($Current.Keys + $Pending.Keys | Sort-Object -Unique)
    foreach ($name in $allNames) {
        if ($keyNames -contains $name) {
            continue
        }
        if (-not $Current.ContainsKey($name) -or -not $Pending.ContainsKey($name) -or
                $Current[$name] -cne $Pending[$name]) {
            throw 'Local integration settings changed after rotation was prepared. Prepare a new rotation before activating.'
        }
    }
}

if ($Mode -eq 'CopyExternalKey') {
    Assert-GitIgnored $settingsPath
    $settings = Read-LocalSettings $settingsPath
    Assert-KeysPresent $settings
    try {
        Set-Clipboard -Value $settings['DEMO_EXTERNAL_API_KEY']
    }
    catch {
        throw 'Could not copy the external API key. No key was displayed; check clipboard access and retry.'
    }
    Write-Output 'The external API key was copied to the clipboard and was not displayed.'
    return
}

Assert-GitIgnored $settingsPath
$settings = Read-LocalSettings $settingsPath
Assert-KeysPresent $settings

if ($Mode -eq 'SecureLocalConfig') {
    Protect-LocalFile $settingsPath
    Write-Output 'Local integration settings ACL is restricted to the current user and SYSTEM; no key values were displayed.'
    return
}

if ($Mode -eq 'Prepare') {
    Assert-GitIgnored $pendingPath
    $pending = @{}
    foreach ($name in $settings.Keys) {
        $pending[$name] = $settings[$name]
    }
    foreach ($name in $keyNames) {
        do {
            $replacement = New-SecureApiKey
        } while ($settings.Values -ccontains $replacement -or $pending.Values -ccontains $replacement)
        $pending[$name] = $replacement
    }
    Write-PendingSettings $pending
    Write-Output 'A replacement key pair was prepared in Git-ignored local settings. Running services are unchanged.'
    Write-Output 'Schedule activation for a maintenance window; no key values were displayed.'
    return
}

Assert-GitIgnored $pendingPath
Assert-GitIgnored $backupPath
Assert-ApiListenersStopped
$pending = Read-LocalSettings $pendingPath
Assert-KeysPresent $pending
Assert-NoStaleSettings $settings $pending
if (Test-Path -LiteralPath $backupPath) {
    throw 'A prior rotation backup already exists. Inspect and remove it manually before activation.'
}

try {
    [System.IO.File]::Replace($pendingPath, $settingsPath, $backupPath)
}
catch {
    throw ('Could not atomically activate the pending key pair ({0}).' -f $_.Exception.GetType().Name)
}

$liveAclRestricted = $true
try {
    Protect-LocalFile $settingsPath
}
catch {
    $liveAclRestricted = $false
}

$backupAclRestricted = $true
try {
    Protect-LocalFile $backupPath
}
catch {
    $backupAclRestricted = $false
}
try {
    Remove-Item -LiteralPath $backupPath -Force -ErrorAction Stop
}
catch {
    throw 'The replacement key pair is active, but the prior-key backup remains at backend/.env.integration.previous.local. Restrict its access and remove it manually; do not retry activation.'
}
if (-not $backupAclRestricted) {
    Write-Warning 'The prior-key backup was removed, but its ACL could not be restricted before deletion.'
}
if (-not $liveAclRestricted) {
    throw 'The replacement key pair is active, but its local file ACL could not be restricted. Run this script with -Mode SecureLocalConfig before starting either API.'
}

Write-Output 'The replacement key pair is active in local configuration. No process was started or stopped.'
Write-Output 'Start the mock and Spring Boot manually so each loads the new pair; key values were not displayed.'
