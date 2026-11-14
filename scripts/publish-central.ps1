[CmdletBinding()]
param(
    [switch]$Upload,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$allowedNames = @('CENTRAL_TOKEN_USERNAME', 'CENTRAL_TOKEN_PASSWORD', 'MAVEN_GPG_PASSPHRASE', 'GPG_KEYNAME')
$savedEnvironment = @{}
foreach ($name in ($allowedNames + @('JAVA_HOME', 'Path'))) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    # Load only publishing configuration. Trading wallet secrets are never imported.
    $envPath = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $envPath) {
        $lineNumber = 0
        foreach ($raw in [IO.File]::ReadAllLines($envPath, [Text.Encoding]::UTF8)) {
            $lineNumber++
            $line = $raw.Trim().TrimStart([char]0xFEFF)
            if ($line -notmatch '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($name -notin $allowedNames) { continue }
            if ([Environment]::GetEnvironmentVariable($name, 'Process')) { continue }
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                $quote = $value[0]
                $end = $value.IndexOf($quote, 1)
                if ($end -lt 0) { throw "Invalid publishing configuration at .env line $lineNumber" }
                $tail = $value.Substring($end + 1).Trim()
                if ($tail -and -not $tail.StartsWith('#')) {
                    throw "Invalid publishing configuration at .env line $lineNumber"
                }
                $value = $value.Substring(1, $end - 1)
            } else {
                $value = ($value -replace '\s+#.*$', '').Trim()
            }
            if ($value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
        }
    }

    if ($JavaHome) {
        $javaExecutable = Join-Path $JavaHome 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'JavaHome must point to a JDK 17 installation' }
        $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
        $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    }
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    $null = Get-Command gpg -ErrorAction Stop
    $mavenVersion = (& $maven -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $mavenVersion -notmatch 'Java version:\s*17(?:\.|\s|,)') {
        throw 'Maven must use JDK 17. Run with -JavaHome pointing to JDK 17.'
    }
    if ($Upload) {
        foreach ($name in @('CENTRAL_TOKEN_USERNAME', 'CENTRAL_TOKEN_PASSWORD')) {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
                throw "Set $name in .env or the process environment before uploading"
            }
        }
    }

    $mavenArguments = @(
        '--settings', (Join-Path $projectRoot '.mvn\central-settings.xml'),
        '-Pdeploy', '-Dmaven.test.skip=true',
        ('-Dcentral.skipUpload=' + (-not $Upload.IsPresent).ToString().ToLowerInvariant())
    )
    if ($env:GPG_KEYNAME) { $mavenArguments += ('-Dgpg.keyname=' + $env:GPG_KEYNAME) }
    $mavenArguments += @('clean', 'deploy')

    Push-Location -LiteralPath $projectRoot
    try {
        if ($Upload) {
            Write-Host 'Uploading for Central validation. Final publishing remains manual in the Portal.'
        } else {
            Write-Host 'Creating a signed local bundle only. No upload or publishing will occur.'
        }
        & $maven @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
