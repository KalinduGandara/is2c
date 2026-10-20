# Shared helpers for the Windows scripts (dot-sourced). Windows PowerShell 5.1 compatible.

$InstallDir = Split-Path $PSScriptRoot -Parent
$PgBin = Join-Path $InstallDir 'pgsql\bin'
$DefaultDataDir = Join-Path $env:ProgramData 'PremiumHRM'

# Well-known SIDs (names are localised on non-English Windows; SIDs are not)
$SidSystem = '*S-1-5-18'
$SidLocalService = '*S-1-5-19'
$SidNetworkService = '*S-1-5-20'
$SidAdministrators = '*S-1-5-32-544'

$script:LogFile = $null

function Write-Log([string]$Message) {
    $line = '{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $Message
    Write-Host $line
    if ($script:LogFile) { [IO.File]::AppendAllText($script:LogFile, $line + "`r`n", (New-Object Text.UTF8Encoding $false)) }
}

# UTF-8 without BOM: Java .properties, Caddyfile and postgresql.conf must not start with a BOM.
function Write-Utf8File([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding $false))
}

# key=value file; blank lines, '#' comments and '[section]' headers are skipped. Values are taken verbatim.
function Read-KeyValueFile([string]$Path) {
    $map = @{}
    foreach ($raw in [IO.File]::ReadAllLines($Path)) {
        $line = $raw.TrimStart([char]0xFEFF)
        if ($line -match '^\s*(#|\[|$)') { continue }
        $i = $line.IndexOf('=')
        if ($i -gt 0) { $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1) }
    }
    return $map
}

function Get-HrmPaths([string]$DataDir) {
    $config = Join-Path $DataDir 'config'
    [pscustomobject]@{
        Data       = $DataDir
        Config     = $config
        Properties = Join-Path $config 'hrm.properties'
        Superuser  = Join-Path $config 'postgres.properties'
        Install    = Join-Path $config 'install.ini'
        PgData     = Join-Path $DataDir 'pgdata'
        Files      = Join-Path $DataDir 'files'
        Backups    = Join-Path $DataDir 'backups'
        Logs       = Join-Path $DataDir 'logs'
        Caddy      = Join-Path $DataDir 'caddy'
        Tmp        = Join-Path $DataDir 'tmp'
    }
}

# Data folder recorded by the installer, else the default.
function Resolve-DataDir([string]$DataDir) {
    if ($DataDir) { return $DataDir.TrimEnd('\') }
    $recorded = (Get-ItemProperty -Path 'HKLM:\SOFTWARE\IS2C\Premium HRM' -Name DataDir -ErrorAction SilentlyContinue).DataDir
    if ($recorded) { return $recorded.TrimEnd('\') }
    return $DefaultDataDir
}

# Runs a native program, logs its combined output and fails on a non-zero exit code. Returns the output lines.
# (Windows PowerShell 5.1 turns native stderr into errors, so 'Stop' is relaxed while it runs.)
function Invoke-Native([string]$Exe, [string[]]$ArgList, [switch]$Quiet) {
    $eap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = @(& $Exe @ArgList 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $eap
    }
    if (-not $Quiet) { foreach ($l in $out) { Write-Log "    $l" } }
    if ($code -ne 0) { throw "$(Split-Path $Exe -Leaf) $($ArgList -join ' ') failed with exit code $code" }
    return $out
}

function Wait-PostgresReady([int]$Port, [int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        & (Join-Path $PgBin 'pg_isready.exe') -h 127.0.0.1 -p $Port -q
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw "PostgreSQL did not accept connections on port $Port within $TimeoutSeconds s (see pgdata\log)"
}
