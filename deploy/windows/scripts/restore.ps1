# Restores a backup set taken by backup.ps1 (or by deploy/backup/backup.sh on Docker: same formats).
# REPLACES the current database and uploaded documents. From an elevated PowerShell:
#   powershell -ExecutionPolicy Bypass -File "C:\Program Files\Premium HRM\scripts\restore.ps1" -Timestamp 20261007-020000
# The app service is stopped while restoring and started again afterwards.
param(
    [Parameter(Mandatory = $true)][string]$Timestamp,
    [string]$DataDir = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$paths = Get-HrmPaths (Resolve-DataDir $DataDir)
$script:LogFile = Join-Path $paths.Logs 'restore.log'
$settings = Read-KeyValueFile $paths.Install
$props = Read-KeyValueFile $paths.Properties
$dir = if ($settings.BackupDir) { $settings.BackupDir } else { $paths.Backups }
$db = Join-Path $dir "hrm-db-$Timestamp.dump"
$files = Join-Path $dir "hrm-files-$Timestamp.tar.gz"
foreach ($f in $db, $files) { if (-not (Test-Path $f)) { Write-Error "missing $f (backup sets are in $dir)" } }

if (-not $Force) {
    $answer = Read-Host "This replaces ALL current Premium HRM data with backup $Timestamp. Type RESTORE to continue"
    if ($answer -ne 'RESTORE') { Write-Host 'Cancelled.'; exit 1 }
}

try {
    Write-Log "restore $Timestamp from $dir"
    Stop-Service HRM-App -Force
    if ((Get-Service HRM-PostgreSQL).Status -ne 'Running') { Start-Service HRM-PostgreSQL }
    Wait-PostgresReady ([int]$settings.DbPort)

    # single transaction: a failure leaves the database as it was
    $env:PGPASSWORD = $props.HRM_DB_PASSWORD
    try {
        [void](Invoke-Native (Join-Path $PgBin 'pg_restore.exe') @('-h', '127.0.0.1', '-p', $settings.DbPort, '-U', $props.HRM_DB_USER,
            '-d', 'hrm', '--clean', '--if-exists', '--no-owner', '--single-transaction', $db))
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    Get-ChildItem -Path $paths.Files -Force | Remove-Item -Recurse -Force
    [void](Invoke-Native (Join-Path $env:SystemRoot 'System32\tar.exe') @('-xzf', $files, '-C', $paths.Files))

    Start-Service HRM-App
    Write-Log "restored $Timestamp; the app is starting (sign in and spot-check the data)"
} catch {
    Write-Log "restore FAILED: $($_.Exception.Message)"
    Write-Log 'Start the app again with: Start-Service HRM-App'
    exit 1
}
