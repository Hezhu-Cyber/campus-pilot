param(
    [int]$Port = 8011
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Join-Path $root '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw "Python virtual environment not found. Run: python -m venv --system-site-packages .venv"
}

try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
    if ($health.status -eq 'UP') {
        Write-Host "CampusPilot assistant is already running on port $Port."
        exit 0
    }
} catch {
    # No healthy assistant is listening; start a new process below.
}

Push-Location $root
try {
    & $python -m uvicorn app.main:app --host 127.0.0.1 --port $Port
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
