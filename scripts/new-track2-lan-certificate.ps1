param(
    [Parameter(Mandatory = $true)]
    [string]$HostName
)

$ErrorActionPreference = 'Stop'

if ($HostName -cnotmatch '^track2-[a-z0-9-]+\.lan$') {
    throw 'Use a unique DNS name such as track2-a.lan or track2-b.lan.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$settingsPath = Join-Path $backendDirectory '.env.integration.local'
$certificateDirectory = Join-Path $env:LOCALAPPDATA 'Track2-HTTPS'
$safeName = $HostName.Replace('.', '-')
$pfxPath = Join-Path $certificateDirectory "$safeName-server.pfx"
$caPath = Join-Path $certificateDirectory "$safeName-ca.cer"

& git -C $projectRoot check-ignore -q -- 'backend/.env.integration.local'
if ($LASTEXITCODE -ne 0) {
    throw 'Refusing to store local TLS settings because backend/.env.integration.local is not Git-ignored.'
}
if ((Test-Path -LiteralPath $pfxPath) -or (Test-Path -LiteralPath $caPath)) {
    throw 'A LAN certificate file already exists for this host. Do not overwrite or silently rotate it.'
}

$settings = [ordered]@{}
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

function Protect-LocalFile([string]$Path) {
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $systemSid = 'S-1-5-18'
    & icacls.exe $Path /inheritance:r | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not disable inherited permissions for a local integration file.'
    }
    $acl = Get-Acl -LiteralPath $Path
    $principals = @{}
    foreach ($rule in $acl.Access) {
        try {
            $sid = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        }
        catch {
            throw 'Could not identify an existing file-permission principal.'
        }
        $principals[$sid] = $true
    }
    foreach ($sid in @($principals.Keys)) {
        if ($sid -eq $currentSid -or $sid -eq $systemSid) {
            continue
        }
        & icacls.exe $Path /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not remove an unapproved file-permission principal.'
        }
    }
    foreach ($sid in @($currentSid, $systemSid)) {
        & icacls.exe $Path /remove "*$sid" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not replace the local integration file permissions.'
        }
    }
    & icacls.exe $Path /grant:r "*$currentSid`:(F)" /grant:r "*$systemSid`:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not protect a local integration file.'
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
        throw 'The local integration file ACL did not match the user-and-SYSTEM-only policy.'
    }
}

New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null
$ca = New-SelfSignedCertificate `
    -Type Custom `
    -Subject "CN=Track2 LAN Integration CA $HostName" `
    -CertStoreLocation 'Cert:\CurrentUser\My' `
    -KeyAlgorithm RSA `
    -KeyLength 3072 `
    -KeyExportPolicy NonExportable `
    -KeyUsage CertSign, CRLSign, DigitalSignature `
    -NotAfter (Get-Date).AddYears(2) `
    -TextExtension @('2.5.29.19={critical}{text}ca=1&pathlength=0')
$serverCertificate = New-SelfSignedCertificate `
    -Type Custom `
    -Subject "CN=$HostName" `
    -DnsName $HostName `
    -Signer $ca `
    -CertStoreLocation 'Cert:\CurrentUser\My' `
    -KeyAlgorithm RSA `
    -KeyLength 3072 `
    -KeyExportPolicy Exportable `
    -KeyUsage DigitalSignature, KeyEncipherment `
    -NotAfter (Get-Date).AddYears(1) `
    -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.1')

$randomBytes = New-Object byte[] 32
$randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomGenerator.GetBytes($randomBytes)
}
finally {
    $randomGenerator.Dispose()
}
$keyStorePassword = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$securePassword = ConvertTo-SecureString -String $keyStorePassword -AsPlainText -Force
Export-PfxCertificate -Cert $serverCertificate -FilePath $pfxPath -Password $securePassword -ChainOption EndEntityCertOnly |
    Out-Null
Export-Certificate -Cert $ca -FilePath $caPath -Type CERT | Out-Null
Protect-LocalFile $pfxPath

$settings['TRACK2_LAN_API_ENABLED'] = 'false'
$settings['TRACK2_LAN_API_BIND_ADDRESS'] = ''
$settings['TRACK2_LAN_API_PARTNER_ADDRESS'] = ''
$settings['TRACK2_LAN_API_PORT'] = '8443'
$settings['TRACK2_LAN_API_KEYSTORE'] = $pfxPath
$settings['TRACK2_LAN_API_KEYSTORE_PASSWORD'] = $keyStorePassword
$lines = @($settings.Keys | Sort-Object | ForEach-Object { '{0}={1}' -f $_, $settings[$_] })
[System.IO.File]::WriteAllLines($settingsPath, $lines, [System.Text.UTF8Encoding]::new($false))
Protect-LocalFile $settingsPath

Write-Output "Created server certificate for $HostName."
Write-Output "Public CA certificate: $caPath"
Write-Output 'The private key and password remain local. The LAN listener is still disabled.'
