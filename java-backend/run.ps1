param(
    [ValidateSet('Check', 'Test', 'Database', 'Run')]
    [string]$Action = 'Check'
)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $configurationPath = Join-Path $PSScriptRoot '.env.real'
    if (Test-Path $configurationPath) {
        foreach ($entry in Get-Content -LiteralPath $configurationPath -Encoding UTF8) {
            if ($entry -match '^\s*(?:#|$)') { continue }
            if ($entry -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
                throw 'Invalid local configuration syntax. Use KEY=value, one entry per line.'
            }
            $key = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }

    if ($Action -eq 'Check') {
        $missing = @()
        foreach ($key in @('DATABASE_URL', 'DATABASE_USER', 'DATABASE_PASSWORD',
                'OPENAI_BASE_URL', 'OPENAI_MODEL', 'OPENAI_API_KEY',
                'EMBEDDING_BASE_URL', 'EMBEDDING_MODEL', 'EMBEDDING_API_KEY')) {
            $configured = -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))
            if ($configured) { Write-Host "$key : configured" }
            else { Write-Host "$key : missing"; $missing += $key }
        }
        Write-Host 'Presence check only; connectivity and model capabilities are not verified.'
        if ($missing.Count -gt 0) { exit 2 }
        exit 0
    }

    if ($Action -eq 'Database') {
        if ([string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
            throw 'Configure DATABASE_PASSWORD locally before starting the database.'
        }
        & docker compose --env-file .env.real up -d --wait database
        exit $LASTEXITCODE
    }

    $jdkCandidates = @($env:JAVA21_HOME, $env:JAVA_HOME, 'D:\DownLoad\Java\jdk21')
    $jdk = $jdkCandidates | Where-Object {
        $_ -and (Test-Path (Join-Path $_ 'release')) -and
        ((Get-Content (Join-Path $_ 'release')) -match '^JAVA_VERSION="21[."]')
    } | Select-Object -First 1
    if (-not $jdk) { throw 'Configure JAVA21_HOME with a JDK 21 installation directory.' }
    $env:JAVA_HOME = $jdk
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $maven = if ($mavenCommand) { $mavenCommand.Source } else {
        Join-Path $HOME '.maven\maven-3.9.16\bin\mvn.cmd'
    }
    if (-not (Test-Path $maven)) { throw 'Install Maven and add its bin directory to PATH.' }

    if ($Action -eq 'Test') {
        & $maven test -B -ntp
        exit $LASTEXITCODE
    }

    foreach ($key in @('DATABASE_URL', 'DATABASE_USER', 'DATABASE_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) {
            throw "Configure $key locally before starting the application."
        }
    }
    & $maven spring-boot:run -B -ntp
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}