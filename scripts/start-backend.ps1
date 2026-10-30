$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$databaseEnvPath = Join-Path $projectRoot 'database\.env'
$backendEnvPath = Join-Path $backendDirectory '.env'
$javaHome = Join-Path $projectRoot 'tools\jdk-21'
$maven = 'C:\Maven\apache-maven-3.9.9\bin\mvn.cmd'
$mavenRepository = Join-Path $projectRoot 'tools\maven-repo'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$expectedDatabaseUrl = 'jdbc:postgresql://127.0.0.1:55432/track2_department'

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
$env:SERVER_ADDRESS = '127.0.0.1'
$env:PORT = '8080'

if (-not (Test-Path -LiteralPath $mavenRepository -PathType Container)) {
    New-Item -ItemType Directory -Path $mavenRepository -Force | Out-Null
}

Write-Output 'Verified database: track2_app at 127.0.0.1:55432/track2_department.'
Write-Output 'Starting Spring Boot with the dev profile on 127.0.0.1:8080.'
Set-Location -LiteralPath $backendDirectory
& $maven --batch-mode --no-transfer-progress `
    "-Dmaven.repo.local=$mavenRepository" `
    '-Dspring-boot.run.profiles=dev' `
    spring-boot:run

exit $LASTEXITCODE
