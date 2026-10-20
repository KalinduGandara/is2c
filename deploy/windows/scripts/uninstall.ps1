# Removes what setup.ps1 registered: services, backup task, firewall rule, trusted root certificate.
# Called by the uninstaller before the program files are deleted. The data folder is left alone (the uninstaller
# asks separately whether to delete it).
param([string]$DataDir = '')

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\common.ps1"

$paths = Get-HrmPaths (Resolve-DataDir $DataDir)
if (Test-Path $paths.Logs) { $script:LogFile = Join-Path $paths.Logs 'uninstall.log' }

function Invoke-Step([string]$Name, [scriptblock]$Step) {
    try { & $Step } catch { Write-Log "WARNING: $Name failed: $($_.Exception.Message)" }
}

Write-Log '=== Premium HRM uninstall'
foreach ($svc in 'HRM-Caddy', 'HRM-App', 'HRM-PostgreSQL') {
    Stop-Service $svc -Force -ErrorAction SilentlyContinue
}
if (Get-Service HRM-Caddy -ErrorAction SilentlyContinue) {
    Invoke-Step 'remove HRM-Caddy' { [void](Invoke-Native (Join-Path $env:SystemRoot 'System32\sc.exe') @('delete', 'HRM-Caddy')) }
}
if (Get-Service HRM-App -ErrorAction SilentlyContinue) {
    Invoke-Step 'remove HRM-App' { [void](Invoke-Native (Join-Path $InstallDir 'service\hrm-app.exe') @('uninstall')) }
}
if (Get-Service HRM-PostgreSQL -ErrorAction SilentlyContinue) {
    Invoke-Step 'remove HRM-PostgreSQL' { [void](Invoke-Native (Join-Path $PgBin 'pg_ctl.exe') @('unregister', '-N', 'HRM-PostgreSQL')) }
}
Invoke-Step 'remove backup task' { Unregister-ScheduledTask -TaskName 'Premium HRM backup' -Confirm:$false -ErrorAction Stop }
Invoke-Step 'remove firewall rule' { Get-NetFirewallRule -Group 'Premium HRM' -ErrorAction SilentlyContinue | Remove-NetFirewallRule }
if (Test-Path $paths.Install) {
    $thumb = (Read-KeyValueFile $paths.Install).RootCertThumbprint
    if ($thumb) {
        Invoke-Step 'remove trusted root certificate' { Remove-Item "Cert:\LocalMachine\Root\$thumb" -ErrorAction Stop }
    }
}
Write-Log 'services, backup task, firewall rule and certificate removed'
exit 0
