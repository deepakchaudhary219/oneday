# Creates .env for `docker compose up` with fresh random secrets.
# Works in Windows PowerShell 5.1 and PowerShell 7:
#   powershell -ExecutionPolicy Bypass -File scripts\new-env.ps1
param([switch]$Force)
$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
if ((Test-Path $envFile) -and -not $Force) {
    Write-Host ".env already exists. To replace it run with -Force, then 'docker compose down -v' so MySQL and Redis start with the new passwords."
    exit 1
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $rng.GetBytes($buffer)
    return -join ($buffer | ForEach-Object { $_.ToString('x2') })
}

$lines = @(
    "MYSQL_PASSWORD=$(New-Secret 16)",
    "REDIS_PASSWORD=$(New-Secret 16)",
    "ONEDAY_JWT_SECRET=$(New-Secret 48)",
    "ONEDAY_LOCATION_SECRET=$(New-Secret 48)",
    "MEDIA_ACCESS_KEY=oneday$(New-Secret 6)",
    "MEDIA_SECRET_KEY=$(New-Secret 20)",
    "ONEDAY_BOOTSTRAP_ADMIN_IDS="
)
# ASCII with LF endings: a byte-order mark would corrupt the first variable name for Docker Compose.
[System.IO.File]::WriteAllText($envFile, ($lines -join "`n") + "`n", [System.Text.Encoding]::ASCII)
Write-Host "Wrote $envFile"
