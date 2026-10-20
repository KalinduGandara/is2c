# One backup: PostgreSQL custom-format dump + archive of uploaded documents, then prune old sets (NFR-011).
# Same file names and formats as deploy/backup/backup.sh, so sets restore on either platform.
# Runs daily as the "Premium HRM backup" scheduled task. By hand, from an elevated PowerShell:
#   powershell -ExecutionPolicy Bypass -File "C:\Program Files\Premium HRM\scripts\backup.ps1"
param([string]$DataDir = '')

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$paths = Get-HrmPaths (Resolve-DataDir $DataDir)
$script:LogFile = Join-Path $paths.Logs 'backup.log'
try {
    $settings = Read-KeyValueFile $paths.Install
    $props = Read-KeyValueFile $paths.Properties
    $dir = if ($settings.BackupDir) { $settings.BackupDir } else { $paths.Backups }
    $retention = if ($settings.BackupRetentionDays) { [int]$settings.BackupRetentionDays } else { 14 }
    $ts = Get-Date -Format 'yyyyMMdd-HHmmss'
    $db = Join-Path $dir "hrm-db-$ts.dump"
    $files = Join-Path $dir "hrm-files-$ts.tar.gz"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $env:PGPASSWORD = $props.HRM_DB_PASSWORD
    try {
        [void](Invoke-Native (Join-Path $PgBin 'pg_dump.exe') @('-h', '127.0.0.1', '-p', $settings.DbPort, '-U', $props.HRM_DB_USER,
            '-d', 'hrm', '-Fc', '--no-owner', '-f', "$db.partial"))
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    Move-Item "$db.partial" $db -Force
    [void](Invoke-Native (Join-Path $env:SystemRoot 'System32\tar.exe') @('-czf', "$files.partial", '-C', $paths.Files, '.'))
    Move-Item "$files.partial" $files -Force

    Get-ChildItem -Path $dir -File -Filter 'hrm-*' |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$retention) } |
        Remove-Item -Force
    Write-Log ('backup {0} done: {1:N1} MB database, {2:N1} MB files, in {3}' -f $ts, ((Get-Item $db).Length / 1MB),
        ((Get-Item $files).Length / 1MB), $dir)
    exit 0
} catch {
    Write-Log "backup FAILED: $($_.Exception.Message)"
    exit 1
}
