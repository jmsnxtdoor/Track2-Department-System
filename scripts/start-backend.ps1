param(
    [string]$ServerAddress = '127.0.0.1'
)

$ErrorActionPreference = 'Stop'

$parsedServerAddress = $null

if (-not [System.Net.IPAddress]::TryParse($ServerAddress, [ref]$parsedServerAddress) -or
    $parsedServerAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw 'ServerAddress must be an IPv4 address assigned to this computer.'
}

if ($ServerAddress -ne '127.0.0.1') {
    $addressBytes = $parsedServerAddress.GetAddressBytes()
    $isPrivateAddress = $addressBytes[0] -eq 10 -or
        ($addressBytes[0] -eq 172 -and $addressBytes[1] -ge 16 -and $addressBytes[1] -le 31) -or
        ($addressBytes[0] -eq 192 -and $addressBytes[1] -eq 168)
    if (-not $isPrivateAddress) {
        throw 'LAN binding requires a private RFC 1918 IPv4 address.'
    }

    $localAddress = Get-NetIPAddress -AddressFamily IPv4 -IPAddress $ServerAddress -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $localAddress) {
        throw 'LAN binding requires an IPv4 address assigned to this computer.'
    }

    $connectionProfile = Get-NetConnectionProfile -InterfaceIndex $localAddress.InterfaceIndex -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $connectionProfile -or $connectionProfile.NetworkCategory -ne 'Private') {
        throw 'LAN binding is allowed only on a Windows network profile classified as Private.'
    }
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$databaseEnvPath = Join-Path $projectRoot 'database\.env'
$backendEnvPath = Join-Path $backendDirectory '.env'
$javaHome = Join-Path $projectRoot 'tools\jdk-21'
$maven = 'C:\Maven\apache-maven-3.9.9\bin\mvn.cmd'
$mavenRepository = Join-Path $projectRoot 'tools\maven-repo'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$expectedDatabaseUrl = 'jdbc:postgresql://127.0.0.1:55432/track2_department'

if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe') -PathType Leaf)) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf)) {
        $javaHome = $env:JAVA_HOME
    }
    else {
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($null -ne $javaCommand) {
            $javaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
        }
    }
}

if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -ne $mavenCommand) {
        $maven = $mavenCommand.Source
    }
}

if (-not (Test-Path -LiteralPath $psql -PathType Leaf)) {
    $postgresInstallations = 'C:\Program Files\PostgreSQL'
    if (Test-Path -LiteralPath $postgresInstallations -PathType Container) {
        $psqlCandidates = Get-ChildItem -LiteralPath $postgresInstallations -Directory |
            Sort-Object { [int]$_.Name } -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\psql.exe' }
        $availablePsql = $psqlCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
        if ($availablePsql) {
            $psql = $availablePsql
        }
    }
}

function Read-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required environment file is missing: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) {
            continue
        }

        $separator = $text.IndexOf('=')
        if ($separator -lt 1) {
            throw "Malformed environment entry in $Path."
        }

        $key = $text.Substring(0, $separator).Trim()
        $value = $text.Substring($separator + 1)
        if ($values.ContainsKey($key)) {
            throw "Duplicate environment key '$key' in $Path."
        }

        $values[$key] = $value
    }

    return $values
}

$databaseSettings = Read-EnvFile $databaseEnvPath
$accountSettings = Read-EnvFile $backendEnvPath

if ($databaseSettings['DATABASE_URL'] -cne $expectedDatabaseUrl) {
    throw 'Refusing to start: DATABASE_URL is not the approved Track 2 development database.'
}
if ($databaseSettings['DATABASE_USER'] -cne 'track2_app' -or
    [string]::IsNullOrWhiteSpace($databaseSettings['DATABASE_PASSWORD'])) {
    throw 'Refusing to start: Track 2 database credentials are incomplete or unexpected.'
}

foreach ($key in @('ADMIN_PASSWORD', 'TREASURER_PASSWORD', 'OFFICER_PASSWORD', 'STUDENT_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($accountSettings[$key])) {
        throw "Required development account credential '$key' is missing."
    }
}

foreach ($path in @(
    (Join-Path $javaHome 'bin\java.exe'),
    (Join-Path $javaHome 'bin\javac.exe'),
    $maven,
    $psql
)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required local tool is missing: $path"
    }
}

$applicationProperties = Get-Content -LiteralPath (Join-Path $backendDirectory 'src\main\resources\application.properties') -Raw
if ($applicationProperties -match 'jdbc:postgresql://[^/\s:]+:5432/' -or
    $applicationProperties -match 'jdbc:postgresql://localhost:5432/') {
    throw 'Refusing to start: backend configuration contains a port 5432 JDBC target.'
}

$listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
if ($listeners.Count -gt 0) {
    throw 'Refusing to start: port 8080 is already in use.'
}

$databaseListeners = @(Get-NetTCPConnection -State Listen -LocalPort 55432 -ErrorAction SilentlyContinue)
if ($databaseListeners.Count -eq 0 -or
    @($databaseListeners | Where-Object { $_.LocalAddress -ne '127.0.0.1' }).Count -gt 0) {
    throw 'Refusing to start: the isolated database is not listening only on 127.0.0.1:55432.'
}

$previousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $databaseSettings['DATABASE_PASSWORD']
    $databaseIdentityOutput = @(
        & $psql --no-psqlrc --quiet --tuples-only --no-align --set=ON_ERROR_STOP=1 `
            --host=127.0.0.1 --port=55432 --username=track2_app --dbname=track2_department `
            --command="SELECT json_build_object('user',current_user,'database',current_database(),'host',host(inet_server_addr()),'port',inet_server_port())::text;"
    )
    $psqlExitCode = $LASTEXITCODE
}
finally {
    $env:PGPASSWORD = $previousPgPassword
}

if ($psqlExitCode -ne 0) {
    throw "Track 2 database authentication check failed (psql exit code $psqlExitCode)."
}

$databaseIdentity = (($databaseIdentityOutput -join "`n").Trim() | ConvertFrom-Json -ErrorAction Stop)
if ($databaseIdentity.user -ne 'track2_app' -or
    $databaseIdentity.database -ne 'track2_department' -or
    $databaseIdentity.host -ne '127.0.0.1' -or
    [int]$databaseIdentity.port -ne 55432) {
    throw 'Authenticated database identity did not match the approved Track 2 target.'
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:DATABASE_URL = $expectedDatabaseUrl
$env:DATABASE_USER = 'track2_app'
$env:DATABASE_PASSWORD = $databaseSettings['DATABASE_PASSWORD']
$env:ADMIN_PASSWORD = $accountSettings['ADMIN_PASSWORD']
$env:TREASURER_PASSWORD = $accountSettings['TREASURER_PASSWORD']
$env:OFFICER_PASSWORD = $accountSettings['OFFICER_PASSWORD']
$env:STUDENT_PASSWORD = $accountSettings['STUDENT_PASSWORD']
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_ADDRESS = $ServerAddress
$env:PORT = '8080'

if (-not (Test-Path -LiteralPath $mavenRepository -PathType Container)) {
    New-Item -ItemType Directory -Path $mavenRepository -Force | Out-Null
}

Write-Output 'Verified database: track2_app at 127.0.0.1:55432/track2_department.'
Write-Output "Starting Spring Boot with the dev profile on ${ServerAddress}:8080."
Set-Location -LiteralPath $backendDirectory
& $maven --batch-mode --no-transfer-progress `
    "-Dmaven.repo.local=$mavenRepository" `
    '-Dspring-boot.run.profiles=dev' `
    spring-boot:run

exit $LASTEXITCODE
