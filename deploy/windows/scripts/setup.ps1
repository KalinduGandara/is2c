# Configures Premium HRM after Inno Setup has copied the program files:
#   data folder + permissions, secrets, PostgreSQL cluster/database/service, app service (WinSW), HTTPS proxy
#   service (Caddy), firewall rule, daily backup task, then waits until the app is healthy.
# Re-runnable: when <DataDir>\config\hrm.properties exists (upgrade, repair, or reinstall onto kept data) the
# existing secrets, ports and database are reused and only services/files are refreshed.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File setup.ps1 -AnswersFile <key=value file from the wizard>
param([Parameter(Mandatory = $true)][string]$AnswersFile)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. "$PSScriptRoot\common.ps1"
. "$PSScriptRoot\check-port.ps1"

$Icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
$Sc = Join-Path $env:SystemRoot 'System32\sc.exe'
$AppService = 'HRM-App'
$DbService = 'HRM-PostgreSQL'
$ProxyService = 'HRM-Caddy'
$BackupTask = 'Premium HRM backup'
$InstallKeys = 'Hostname', 'HttpPort', 'HttpsPort', 'AppPort', 'DbPort', 'BackupAt', 'BackupRetentionDays', 'BackupDir',
    'JavaOptions', 'RootCertThumbprint'

function New-Secret([int]$Bytes) {
    $b = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($b) } finally { $rng.Dispose() }
    return ([Convert]::ToBase64String($b) -replace '[+/=]', '')
}

# Java .properties escaping: backslash, non-ASCII as \uXXXX (the loader's charset then does not matter), leading space.
function ConvertTo-PropertyValue([string]$Value) {
    $sb = New-Object Text.StringBuilder
    foreach ($c in $Value.ToCharArray()) {
        if ($c -eq [char]'\') { [void]$sb.Append('\\') }
        elseif ([int]$c -gt 126 -or [int]$c -lt 32) { [void]$sb.Append(('\u{0:x4}' -f [int]$c)) }
        else { [void]$sb.Append($c) }
    }
    $s = $sb.ToString()
    if ($s.StartsWith(' ')) { $s = '\' + $s }
    return $s
}

function ConvertTo-ForwardSlash([string]$Path) { return $Path.Replace('\', '/') }

function Save-InstallSettings($Paths, $Settings) {
    $lines = @('# Premium HRM installation settings, written by setup. Run setup again after changing them.', '[install]')
    foreach ($k in $InstallKeys) { $lines += "$k=$($Settings[$k])" }
    Write-Utf8File $Paths.Install (($lines -join "`r`n") + "`r`n")
}

function Grant-Access([string]$Path, [string]$Sid, [string]$Rights) {
    [void](Invoke-Native $Icacls @($Path, '/grant', "${Sid}:$Rights") -Quiet)
}

function Set-ServiceAccount([string]$Name, [string]$Account) {
    $svc = Get-CimInstance Win32_Service -Filter "Name = '$Name'"
    $r = Invoke-CimMethod -InputObject $svc -MethodName Change -Arguments @{ StartName = $Account; StartPassword = '' }
    if ($r.ReturnValue -ne 0) { throw "could not set the logon account of service $Name (Win32 error $($r.ReturnValue))" }
}

function Set-ServicePath([string]$Name, [string]$PathName) {
    $svc = Get-CimInstance Win32_Service -Filter "Name = '$Name'"
    $r = Invoke-CimMethod -InputObject $svc -MethodName Change -Arguments @{ PathName = $PathName }
    if ($r.ReturnValue -ne 0) { throw "could not update service $Name (Win32 error $($r.ReturnValue))" }
}

function Initialize-DataFolder($Paths) {
    foreach ($d in $Paths.Data, $Paths.Config, $Paths.Files, $Paths.Backups, $Paths.Logs, $Paths.Caddy, $Paths.Tmp) {
        New-Item -ItemType Directory -Force -Path $d | Out-Null
    }
    # Only SYSTEM and Administrators by default; each service account gets just what it needs below.
    [void](Invoke-Native $Icacls @($Paths.Data, '/inheritance:r', '/grant:r', "${SidSystem}:(OI)(CI)F",
        "${SidAdministrators}:(OI)(CI)F") -Quiet)
    foreach ($d in $Paths.Files, $Paths.Logs, $Paths.Caddy, $Paths.Tmp) { Grant-Access $d $SidLocalService '(OI)(CI)M' }
}

function New-Configuration($Paths, $Answers) {
    Write-Log 'New installation: choosing ports and generating secrets'
    foreach ($port in [int]$Answers.HttpsPort, [int]$Answers.HttpPort) {
        $owner = Get-PortOwner $port
        if ($owner) { throw "port $port is already used by $owner. Free it or choose another port in the installer." }
    }
    $appPort = Find-FreePort 8080
    $dbPort = Find-FreePort 5432
    if ($appPort -ne 8080) { Write-Log "port 8080 is busy ($(Get-PortOwner 8080)); the app listens on $appPort" }
    if ($dbPort -ne 5432) { Write-Log "port 5432 is busy ($(Get-PortOwner 5432)); PostgreSQL listens on $dbPort" }

    $settings = @{
        Hostname = $Answers.Hostname; HttpPort = $Answers.HttpPort; HttpsPort = $Answers.HttpsPort
        AppPort = "$appPort"; DbPort = "$dbPort"; BackupAt = '02:00'; BackupRetentionDays = '14'; BackupDir = ''
        JavaOptions = '-XX:MaxRAMPercentage=25'; RootCertThumbprint = ''
    }
    Save-InstallSettings $Paths $settings

    Write-Utf8File $Paths.Superuser ("# PostgreSQL superuser 'postgres' (maintenance only; the app uses the 'hrm' role)`r`n" +
        "PG_SUPERUSER_PASSWORD=$(New-Secret 24)`r`n")

    $storage = ConvertTo-ForwardSlash $Paths.Files
    $props = @"
# Premium HRM configuration. Restart the "Premium HRM" service after editing (Services app, or
#   Restart-Service HRM-App). Same settings as deploy/.env.example on Docker; see docs/install-windows.md.
# Keep a copy of this file in a password manager: it is not part of the backups.

HRM_EDITION=ONPREM
HRM_DB_URL=jdbc:postgresql://127.0.0.1:$dbPort/hrm
HRM_DB_USER=hrm
HRM_DB_PASSWORD=$(New-Secret 24)
HRM_JWT_SECRET=$(New-Secret 48)
HRM_STORAGE_PATH=$storage
server.address=127.0.0.1
server.port=$appPort

# First administrator: created only while the database has no users; must change the password at first sign-in.
HRM_ADMIN_USERNAME=$(ConvertTo-PropertyValue $Answers.AdminUser)
HRM_ADMIN_PASSWORD=$(ConvertTo-PropertyValue $Answers.AdminPassword)

# Outgoing email (payslips, approval notifications)
HRM_MAIL_ENABLED=false
#HRM_SMTP_HOST=smtp.example.com
#HRM_SMTP_PORT=587
#HRM_SMTP_USERNAME=
#HRM_SMTP_PASSWORD=
#HRM_SMTP_AUTH=true
#HRM_SMTP_STARTTLS=true
#HRM_MAIL_FROM=hrm@example.com

# OpenAPI docs at /api/docs/ui
HRM_API_DOCS=false
"@
    Write-Utf8File $Paths.Properties ($props -replace "`r?`n", "`r`n")
}

function Install-Database($Paths, $Settings, $Props) {
    $su = Read-KeyValueFile $Paths.Superuser
    $dbPort = [int]$Settings.DbPort

    if (-not (Test-Path (Join-Path $Paths.PgData 'PG_VERSION'))) {
        Write-Log "Creating the PostgreSQL cluster in $($Paths.PgData)"
        New-Item -ItemType Directory -Force -Path $Paths.PgData | Out-Null
        # initdb runs with a restricted token (administrator rights dropped), so the installing account needs its
        # own access while it runs; the service account (NetworkService) keeps full control of the cluster.
        $me = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        Grant-Access $Paths.PgData "*$me" '(OI)(CI)F'
        Grant-Access $Paths.PgData $SidNetworkService '(OI)(CI)F'
        $pwFile = Join-Path $env:TEMP ('hrm-pg-' + [guid]::NewGuid() + '.txt')
        Write-Utf8File $pwFile $su.PG_SUPERUSER_PASSWORD
        try {
            [void](Invoke-Native (Join-Path $PgBin 'initdb.exe') @('-D', $Paths.PgData, '-U', 'postgres', "--pwfile=$pwFile",
                '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C', '--locale-provider=builtin',
                '--builtin-locale=C.UTF-8'))
        } finally {
            Remove-Item $pwFile -Force -ErrorAction SilentlyContinue
        }
        $conf = @"

# --- Premium HRM ---
listen_addresses = 'localhost'
port = $dbPort
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%a.log'
log_truncate_on_rotation = on
log_rotation_age = 1d
"@
        [IO.File]::AppendAllText((Join-Path $Paths.PgData 'postgresql.conf'), ($conf -replace "`r?`n", "`r`n"),
            (New-Object Text.UTF8Encoding $false))
        [void](Invoke-Native $Icacls @($Paths.PgData, '/remove:g', "*$me") -Quiet)
    } else {
        Grant-Access $Paths.PgData $SidNetworkService '(OI)(CI)F'
    }

    if (-not (Get-Service $DbService -ErrorAction SilentlyContinue)) {
        Write-Log "Registering service $DbService"
        [void](Invoke-Native (Join-Path $PgBin 'pg_ctl.exe') @('register', '-N', $DbService, '-U', 'NT AUTHORITY\NetworkService',
            '-D', $Paths.PgData, '-S', 'auto'))
        [void](Invoke-Native $Sc @('description', $DbService, 'Premium HRM database (PostgreSQL)') -Quiet)
        [void](Invoke-Native $Sc @('failure', $DbService, 'reset=', '86400', 'actions=', 'restart/10000/restart/30000/restart/60000') -Quiet)
    }
    if ((Get-Service $DbService).Status -ne 'Running') { Start-Service $DbService }
    Wait-PostgresReady $dbPort

    $env:PGPASSWORD = $su.PG_SUPERUSER_PASSWORD
    try {
        $psql = Join-Path $PgBin 'psql.exe'
        $sql = @('-h', '127.0.0.1', '-p', "$dbPort", '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-tAc')
        $hasRole = (Invoke-Native $psql ($sql + "SELECT 1 FROM pg_roles WHERE rolname = 'hrm'") -Quiet) -join ''
        $dbPassword = $Props.HRM_DB_PASSWORD.Replace("'", "''")
        if ($hasRole.Trim() -ne '1') {
            Write-Log 'Creating database role hrm'
            [void](Invoke-Native $psql ($sql + "CREATE ROLE hrm LOGIN PASSWORD '$dbPassword'") -Quiet)
        } else {
            # keeps the role in step with hrm.properties if an administrator changed the password there
            [void](Invoke-Native $psql ($sql + "ALTER ROLE hrm LOGIN PASSWORD '$dbPassword'") -Quiet)
        }
        $hasDb = (Invoke-Native $psql ($sql + "SELECT 1 FROM pg_database WHERE datname = 'hrm'") -Quiet) -join ''
        if ($hasDb.Trim() -ne '1') {
            Write-Log 'Creating database hrm'
            [void](Invoke-Native $psql ($sql + 'CREATE DATABASE hrm OWNER hrm') -Quiet)
        }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Install-AppService($Paths, $Settings) {
    $esc = { param($s) [Security.SecurityElement]::Escape($s) }
    $java = Join-Path $InstallDir 'jre\bin\java.exe'
    $jar = Join-Path $InstallDir 'app\hrm.jar'
    $propsUrl = 'file:' + (ConvertTo-ForwardSlash $Paths.Properties)
    $arguments = "$($Settings.JavaOptions) -Dfile.encoding=UTF-8 -Djava.io.tmpdir=`"$($Paths.Tmp)`" -jar `"$jar`" " +
        "--spring.config.additional-location=`"$propsUrl`""
    $xml = @"
<!-- Generated by setup.ps1 on every install/upgrade; change JavaOptions in $(& $esc $Paths.Install) instead. -->
<service>
  <id>$AppService</id>
  <name>Premium HRM</name>
  <description>Premium HRM application server. Configuration: $(& $esc $Paths.Properties)</description>
  <executable>$(& $esc $java)</executable>
  <arguments>$(& $esc $arguments)</arguments>
  <env name="SPRING_PROFILES_ACTIVE" value="prod"/>
  <workingdirectory>$(& $esc $Paths.Tmp)</workingdirectory>
  <logpath>$(& $esc $Paths.Logs)</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>8</keepFiles>
  </log>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
  <depend>$DbService</depend>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <onfailure action="restart" delay="60 sec"/>
  <resetfailure>1 hour</resetfailure>
  <stoptimeout>40 sec</stoptimeout>
</service>
"@
    $wrapper = Join-Path $InstallDir 'service\hrm-app.exe'
    Write-Utf8File (Join-Path $InstallDir 'service\hrm-app.xml') $xml
    # WinSW re-reads the XML on every start, so an existing service only needs the new file.
    if (-not (Get-Service $AppService -ErrorAction SilentlyContinue)) {
        Write-Log "Registering service $AppService"
        [void](Invoke-Native $wrapper @('install'))
    }
    Set-ServiceAccount $AppService 'NT AUTHORITY\LocalService'
    Grant-Access $Paths.Properties $SidLocalService 'R'
}

function Install-ProxyService($Paths, $Settings) {
    $caddy = Join-Path $InstallDir 'caddy\caddy.exe'
    $caddyfile = Join-Path $Paths.Caddy 'Caddyfile'
    $sites = @("$($Settings.Hostname):$($Settings.HttpsPort)")
    if ($Settings.Hostname -ne 'localhost') { $sites += "localhost:$($Settings.HttpsPort)" }
    $conf = @"
# Generated by setup.ps1 on every install/upgrade. HTTPS with Caddy's internal CA (root: hrm-root.crt in the data folder).
{
	http_port $($Settings.HttpPort)
	https_port $($Settings.HttpsPort)
	skip_install_trust
	storage file_system {
		root "$(ConvertTo-ForwardSlash $Paths.Caddy)"
	}
	log {
		output file "$(ConvertTo-ForwardSlash (Join-Path $Paths.Logs 'caddy.log'))" {
			roll_size 10MiB
			roll_keep 5
		}
	}
}

$($sites -join ', ') {
	tls internal
	encode gzip
	reverse_proxy 127.0.0.1:$($Settings.AppPort)
}
"@
    Write-Utf8File $caddyfile ($conf -replace "`r?`n", "`r`n")
    $bin = "`"$caddy`" run --config `"$caddyfile`" --adapter caddyfile"
    if (Get-Service $ProxyService -ErrorAction SilentlyContinue) {
        Set-ServicePath $ProxyService $bin
    } else {
        Write-Log "Registering service $ProxyService"
        New-Service -Name $ProxyService -BinaryPathName $bin -DisplayName 'Premium HRM HTTPS proxy' -StartupType Automatic `
            -Description 'HTTPS front end for Premium HRM (Caddy)' | Out-Null
        [void](Invoke-Native $Sc @('failure', $ProxyService, 'reset=', '86400', 'actions=', 'restart/10000/restart/30000/restart/60000') -Quiet)
    }
    Set-ServiceAccount $ProxyService 'NT AUTHORITY\LocalService'

    Get-NetFirewallRule -Group 'Premium HRM' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName 'Premium HRM (HTTP/HTTPS)' -Group 'Premium HRM' -Direction Inbound -Action Allow `
        -Protocol TCP -LocalPort @($Settings.HttpPort, $Settings.HttpsPort) -Program $caddy | Out-Null
}

function Install-BackupTask($Paths, $Settings) {
    $ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $script = Join-Path $InstallDir 'scripts\backup.ps1'
    $action = New-ScheduledTaskAction -Execute $ps `
        -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$script`" -DataDir `"$($Paths.Data)`""
    $trigger = New-ScheduledTaskTrigger -Daily -At $Settings.BackupAt
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    $taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
    Register-ScheduledTask -TaskName $BackupTask -Action $action -Trigger $trigger -Principal $principal `
        -Settings $taskSettings -Description 'Daily Premium HRM database and document backup' -Force | Out-Null
}

function Wait-AppHealthy($Settings, [int]$TimeoutSeconds = 300) {
    $url = "http://127.0.0.1:$($Settings.AppPort)/actuator/health"
    Write-Log "Waiting for $url (first start applies the database migrations)"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($r.Content -match '"status"\s*:\s*"UP"') { return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "the app did not become healthy within $TimeoutSeconds s; see hrm-app.out.log / hrm-app.err.log in the logs folder"
}

# Trusts Caddy's internal root CA on this server and copies it to the data folder for distribution to client PCs.
function Install-RootCertificate($Paths, $Settings) {
    $root = Join-Path $Paths.Caddy 'pki\authorities\local\root.crt'
    $deadline = (Get-Date).AddSeconds(60)
    while (-not (Test-Path $root) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
    if (-not (Test-Path $root)) { Write-Log "WARNING: $root was not created; check logs\caddy.log"; return }
    Copy-Item $root (Join-Path $Paths.Data 'hrm-root.crt') -Force
    $cert = Import-Certificate -FilePath $root -CertStoreLocation 'Cert:\LocalMachine\Root'
    $Settings.RootCertThumbprint = $cert.Thumbprint
    Save-InstallSettings $Paths $Settings
}

# --- main -------------------------------------------------------------------------------------------------------
$answers = Read-KeyValueFile $AnswersFile
$paths = Get-HrmPaths $answers.DataDir.TrimEnd('\')
New-Item -ItemType Directory -Force -Path $paths.Logs | Out-Null
$script:LogFile = Join-Path $paths.Logs 'setup.log'
try {
    Initialize-DataFolder $paths
    Write-Log "=== Premium HRM $($answers.AppVersion) setup: program $InstallDir, data $($paths.Data)"

    if (-not (Test-Path $paths.Properties)) {
        New-Configuration $paths $answers
    } else {
        Write-Log 'Existing configuration found: keeping secrets, ports and data'
    }
    $settings = Read-KeyValueFile $paths.Install
    $props = Read-KeyValueFile $paths.Properties

    Install-Database $paths $settings $props
    Install-AppService $paths $settings
    Install-ProxyService $paths $settings
    Install-BackupTask $paths $settings

    Write-Log 'Starting services'
    Restart-Service $AppService -Force
    Wait-AppHealthy $settings
    Restart-Service $ProxyService -Force
    Install-RootCertificate $paths $settings

    $port = if ($settings.HttpsPort -eq '443') { '' } else { ":$($settings.HttpsPort)" }
    Write-Log "Done. Open https://$($settings.Hostname)$port/"
    exit 0
} catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    Write-Log $_.ScriptStackTrace
    exit 1
}
