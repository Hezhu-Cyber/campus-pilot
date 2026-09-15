param(
    [switch]$SkipMaven
)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$localEnvPath = Join-Path $backendRoot '.env.local'
$assistantEnvPath = Join-Path (Split-Path -Parent $backendRoot) 'assistant-service\.env'

function New-Secret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

if (-not (Test-Path -LiteralPath $localEnvPath)) {
    @(
        "ASSISTANT_INTERNAL_TOKEN=$(New-Secret)",
        "ASSISTANT_USER_CONTEXT_SECRET=$(New-Secret)"
    ) | Set-Content -LiteralPath $localEnvPath -Encoding utf8
}

$values = @{}
Get-Content -LiteralPath $localEnvPath | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=(.+)$') {
        $values[$matches[1]] = $matches[2]
    }
}

if (-not $values['ASSISTANT_INTERNAL_TOKEN'] -or -not $values['ASSISTANT_USER_CONTEXT_SECRET']) {
    throw "backend/.env.local must define ASSISTANT_INTERNAL_TOKEN and ASSISTANT_USER_CONTEXT_SECRET"
}

$env:ASSISTANT_INTERNAL_TOKEN = $values['ASSISTANT_INTERNAL_TOKEN']
$env:ASSISTANT_USER_CONTEXT_SECRET = $values['ASSISTANT_USER_CONTEXT_SECRET']
$env:SPRING_PROFILES_ACTIVE = 'local'

if (Test-Path -LiteralPath $assistantEnvPath) {
    $lines = Get-Content -LiteralPath $assistantEnvPath
    $found = $false
    $updated = $lines | ForEach-Object {
        if ($_ -match '^ASSISTANT_INTERNAL_TOKEN=') {
            $found = $true
            "ASSISTANT_INTERNAL_TOKEN=$($values['ASSISTANT_INTERNAL_TOKEN'])"
        } else {
            $_
        }
    }
    if (-not $found) {
        $updated += "ASSISTANT_INTERNAL_TOKEN=$($values['ASSISTANT_INTERNAL_TOKEN'])"
    }
    $updated | Set-Content -LiteralPath $assistantEnvPath -Encoding utf8
}

Write-Host "Starting CampusPilot with local-only assistant secrets."
Write-Host "Start assistant-service/start.ps1 in another terminal before chatting."
if (-not $SkipMaven) {
    Push-Location $backendRoot
    try {
        & mvn spring-boot:run
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
}
