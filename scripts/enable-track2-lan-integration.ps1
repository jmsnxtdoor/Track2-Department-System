param(
    [Parameter(Mandatory = $true)]
    [string]$BindAddress,
    [Parameter(Mandatory = $true)]
    [string]$PartnerAddress
)

$ErrorActionPreference = 'Stop'

function Test-PrivateIpv4([string]$Address) {
    $parsedAddress = $null
    if (-not [System.Net.IPAddress]::TryParse($Address, [ref]$parsedAddress) -or
        $parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
        $parsedAddress.IsLoopback()) {
        return $false
    }
    $bytes = $parsedAddress.GetAddressBytes()
    return $bytes[0] -eq 10 -or
        ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
}

if (-not (Test-PrivateIpv4 $BindAddress) -or -not (Test-PrivateIpv4 $PartnerAddress)) {
    throw 'Both addresses must be private IPv4 addresses.'
}
if ($BindAddress -eq $PartnerAddress) {
    throw 'The laptop bind address and partner address must be different.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsPath = Join-Path $projectRoot 'backend\.env.integration.local'
& git -C $projectRoot check-ignore -q -- 'backend/.env.integration.local'
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
    throw 'Run scripts/new-track2-lan-certificate.ps1 first to create the protected local integration configuration.'
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
    $name = $text.Substring(0, $separator).Trim()
    if ($settings.ContainsKey($name)) {
        throw 'The local integration configuration contains a duplicate variable.'
    }
    $settings[$name] = $text.Substring($separator + 1)
}

if ([string]::IsNullOrWhiteSpace($settings['TRACK2_LAN_API_KEYSTORE']) -or
    -not (Test-Path -LiteralPath $settings['TRACK2_LAN_API_KEYSTORE'] -PathType Leaf) -or
    [string]::IsNullOrWhiteSpace($settings['TRACK2_LAN_API_KEYSTORE_PASSWORD']) -or
    [string]::IsNullOrWhiteSpace($settings['TRACK2_INTEGRATION_API_KEY'])) {
    throw 'A server keystore, its password, and the existing provider API key are required.'
}
if (@(Get-NetIPAddress -AddressFamily IPv4 -IPAddress $BindAddress -ErrorAction SilentlyContinue).Count -eq 0) {
    throw 'The bind address is not assigned to this laptop.'
}
if (@(Get-NetFirewallRule -DisplayName 'Track2 read-only integration HTTPS' -ErrorAction SilentlyContinue).Count -gt 0) {
    throw 'A firewall rule with this name already exists. Inspect it before changing it.'
}

$port = 8443
if (-not [string]::IsNullOrWhiteSpace($settings['TRACK2_LAN_API_PORT'])) {
    $port = [int]$settings['TRACK2_LAN_API_PORT']
}
if ($port -lt 1024 -or $port -gt 65535) {
    throw 'The configured LAN integration port must be between 1024 and 65535.'
}
if (@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count -gt 0) {
    throw 'The configured LAN integration port is already in use.'
}
$portRule = New-NetFirewallRule `
    -DisplayName 'Track2 read-only integration HTTPS' `
    -Direction Inbound `
    -Action Allow `
    -Enabled True `
    -Protocol TCP `
    -LocalPort $port `
    -LocalAddress $BindAddress `
    -RemoteAddress $PartnerAddress `
    -Profile Any

try {
    $settings['TRACK2_LAN_API_ENABLED'] = 'true'
    $settings['TRACK2_LAN_API_BIND_ADDRESS'] = $BindAddress
    $settings['TRACK2_LAN_API_PARTNER_ADDRESS'] = $PartnerAddress
    $settings['TRACK2_LAN_API_PORT'] = [string]$port
    $lines = @($settings.Keys | Sort-Object | ForEach-Object { '{0}={1}' -f $_, $settings[$_] })
    [System.IO.File]::WriteAllLines($settingsPath, $lines, [System.Text.UTF8Encoding]::new($false))

    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $systemSid = 'S-1-5-18'
    & icacls.exe $settingsPath /inheritance:r | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not disable inherited permissions for the local integration configuration.'
    }
    $acl = Get-Acl -LiteralPath $settingsPath
    $principals = @{}
    foreach ($rule in $acl.Access) {
        $sid = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        $principals[$sid] = $true
    }
    foreach ($sid in @($principals.Keys)) {
        if ($sid -eq $currentSid -or $sid -eq $systemSid) {
            continue
        }
        & icacls.exe $settingsPath /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not remove an unapproved configuration-file principal.'
        }
    }
    foreach ($sid in @($currentSid, $systemSid)) {
        & icacls.exe $settingsPath /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not replace local integration configuration permissions.'
        }
    }
    & icacls.exe $settingsPath /grant:r "*$currentSid`:(F)" /grant:r "*$systemSid`:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not protect the local integration configuration.'
    }
    $verifiedAcl = Get-Acl -LiteralPath $settingsPath
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
        throw 'The local integration file ACL did not match the user-and-SYSTEM-only policy.'
    }
}
catch {
    Disable-NetFirewallRule -AssociatedNetFirewallRule $portRule -ErrorAction SilentlyContinue
    throw
}

Write-Output "Enabled the HTTPS summary listener on $BindAddress`:$port."
Write-Output "Windows Firewall allows only peer $PartnerAddress to connect to that address and port."
Write-Output 'Restart Spring Boot manually when approved; this script does not stop or restart services.'
