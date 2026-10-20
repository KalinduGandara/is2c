<#
Builds the Windows installer: deploy\windows\build\out\PremiumHRM-Setup-<version>.exe

Run on Windows (x64) from any folder:
    powershell -ExecutionPolicy Bypass -File deploy\windows\build.ps1

Needs JDK 21 (JAVA_HOME or java on PATH), Node.js 22 + npm, Inno Setup 6.3+ and internet access. Downloads
(cached in build\cache): Temurin 21 JRE, PostgreSQL Windows binaries, Caddy, WinSW, Visual C++ runtime.
Pin newer versions with the parameters below; keep PostgreSQL on the major version installed at customers
(a major upgrade needs a dump/restore, see docs/install-windows.md).
#>
param(
    [string]$PostgresVersion = '17.6-1',
    [string]$CaddyVersion = '2.10.2',
    [string]$WinSWVersion = '2.12.0',
    [string]$Iscc = '',
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$here = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $here '..\..')).Path
$build = Join-Path $here 'build'
$cache = Join-Path $build 'cache'
$stage = Join-Path $build 'stage'
$out = Join-Path $build 'out'

function Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }

function Exec-Checked([string]$Exe, [string[]]$ArgList) {
    & $Exe @ArgList
    if ($LASTEXITCODE -ne 0) { throw "$Exe $($ArgList -join ' ') failed with exit code $LASTEXITCODE" }
}

function Get-Cached([string]$Url, [string]$Name) {
    $file = Join-Path $cache $Name
    if (-not (Test-Path $file)) {
        Write-Host "    downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile "$file.partial" -UseBasicParsing
        Move-Item "$file.partial" $file
    }
    return $file
}

function Expand-Temp([string]$Zip) {
    $tmp = Join-Path $build ('unzip-' + [guid]::NewGuid())
    Expand-Archive -Path $Zip -DestinationPath $tmp
    return $tmp
}

# --- version ---------------------------------------------------------------------------------------------------
$pom = [xml](Get-Content (Join-Path $repo 'backend\pom.xml'))
$version = $pom.project.version -replace '-SNAPSHOT$', ''
Step "Premium HRM $version"

New-Item -ItemType Directory -Force -Path $cache, $out | Out-Null
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

# --- application jar (SPA embedded, as in deploy/Dockerfile) -----------------------------------------------------
$frontend = Join-Path $repo 'frontend'
$backend = Join-Path $repo 'backend'
$static = Join-Path $backend 'src\main\resources\static'
if (-not $SkipFrontend) {
    Step 'Building the frontend'
    Push-Location $frontend
    try {
        Exec-Checked 'npm.cmd' @('ci', '--no-audit', '--no-fund')
        Exec-Checked 'npm.cmd' @('run', 'build')
    } finally { Pop-Location }
}
Step 'Building the backend jar'
if (Test-Path $static) { throw "$static exists; remove it (it is created and deleted by this script)" }
Copy-Item (Join-Path $frontend 'dist') $static -Recurse
Push-Location $backend
try {
    Exec-Checked (Join-Path $backend 'mvnw.cmd') @('-q', '-B', '-DskipTests', 'clean', 'package')
} finally {
    Pop-Location
    Remove-Item $static -Recurse -Force
}
$jar = Get-ChildItem (Join-Path $backend 'target') -Filter 'hrm-*.jar' | Select-Object -First 1
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'app') | Out-Null
Copy-Item $jar.FullName (Join-Path $stage 'app\hrm.jar')

# --- Java runtime --------------------------------------------------------------------------------------------------
Step 'Java 21 runtime (Temurin JRE)'
$jreZip = Get-Cached 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse' 'temurin-21-jre.zip'
$tmp = Expand-Temp $jreZip
Move-Item (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName (Join-Path $stage 'jre')
Remove-Item $tmp -Recurse -Force

# --- PostgreSQL -------------------------------------------------------------------------------------------------------
Step "PostgreSQL $PostgresVersion"
$pgZip = Get-Cached "https://get.enterprisedb.com/postgresql/postgresql-$PostgresVersion-windows-x64-binaries.zip" `
    "postgresql-$PostgresVersion.zip"
$tmp = Expand-Temp $pgZip
$pg = Join-Path $stage 'pgsql'
Move-Item (Join-Path $tmp 'pgsql') $pg
Remove-Item $tmp -Recurse -Force
# server + client tools only
foreach ($extra in 'pgAdmin 4', 'StackBuilder', 'doc', 'include', 'symbols') {
    $p = Join-Path $pg $extra
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
}

# --- Caddy, WinSW, VC++ runtime -----------------------------------------------------------------------------------
Step "Caddy $CaddyVersion"
$caddyZip = Get-Cached "https://github.com/caddyserver/caddy/releases/download/v$CaddyVersion/caddy_${CaddyVersion}_windows_amd64.zip" `
    "caddy-$CaddyVersion.zip"
$tmp = Expand-Temp $caddyZip
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'caddy') | Out-Null
Move-Item (Join-Path $tmp 'caddy.exe') (Join-Path $stage 'caddy\caddy.exe')
Remove-Item $tmp -Recurse -Force

Step "WinSW $WinSWVersion"
$winsw = Get-Cached "https://github.com/winsw/winsw/releases/download/v$WinSWVersion/WinSW-x64.exe" "winsw-$WinSWVersion-x64.exe"
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'service') | Out-Null
Copy-Item $winsw (Join-Path $stage 'service\hrm-app.exe')

Step 'Visual C++ runtime'
$redist = Get-Cached 'https://aka.ms/vs/17/release/vc_redist.x64.exe' 'vc_redist.x64.exe'

# --- scripts and user guide -----------------------------------------------------------------------------------------
Step 'Scripts and user guide'
Copy-Item (Join-Path $here 'scripts') (Join-Path $stage 'scripts') -Recurse
$guide = Join-Path $stage 'docs\user-guide'
New-Item -ItemType Directory -Force -Path $guide | Out-Null
Copy-Item (Join-Path $repo 'docs\user-guide\index.html') $guide
Copy-Item (Join-Path $repo 'docs\user-guide\images') $guide -Recurse

# --- installer -------------------------------------------------------------------------------------------------------
Step 'Compiling the installer'
if (-not $Iscc) {
    $cmd = Get-Command 'iscc.exe' -ErrorAction SilentlyContinue
    $Iscc = if ($cmd) { $cmd.Source } else { Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe' }
}
if (-not (Test-Path $Iscc)) { throw "Inno Setup compiler not found ($Iscc); install Inno Setup 6.3+ or pass -Iscc" }
Exec-Checked $Iscc @("/DAppVersion=$version", "/DStageDir=$stage", "/DRedistFile=$redist", "/O$out",
    (Join-Path $here 'hrm.iss'))
Step "Done: $(Join-Path $out "PremiumHRM-Setup-$version.exe")"
