# Port conflict check. Two uses:
#   - the installer wizard runs it with -Port/-OutFile: exit 0 = free; exit 1 = in use, owner written to -OutFile
#   - setup.ps1 dot-sources it (no -Port) to use Get-PortOwner / Find-FreePort
param([int]$Port = 0, [string]$OutFile = '')

# Returns '' when the TCP port is free, otherwise a description of what holds it.
function Get-PortOwner([int]$Port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $procId = [int]$listener.OwningProcess
        if ($procId -eq 4) {
            return 'Windows HTTP service (http.sys - usually IIS "World Wide Web Publishing Service" or another web application)'
        }
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $svc = Get-CimInstance Win32_Service -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue | Select-Object -First 1
        $name = if ($svc) { "service '$($svc.DisplayName)'" } elseif ($proc) { "program '$($proc.ProcessName)'" } else { 'a program' }
        return "$name (PID $procId)"
    }
    # Ports Windows keeps for itself (Hyper-V, WSL, Docker) cannot be bound even when nothing listens.
    foreach ($line in (netsh interface ipv4 show excludedportrange protocol=tcp 2>$null)) {
        if ($line -match '^\s*(\d+)\s+(\d+)') {
            if ($Port -ge [int]$Matches[1] -and $Port -le [int]$Matches[2]) {
                return "a range Windows reserves ($($Matches[1])-$($Matches[2]), usually Hyper-V, WSL or Docker)"
            }
        }
    }
    return ''
}

function Find-FreePort([int]$Preferred, [int]$Attempts = 50) {
    for ($p = $Preferred; $p -lt $Preferred + $Attempts; $p++) {
        if (-not (Get-PortOwner $p)) { return $p }
    }
    throw "no free TCP port between $Preferred and $($Preferred + $Attempts - 1)"
}

if ($Port -gt 0) {
    $owner = Get-PortOwner $Port
    if (-not $owner) { exit 0 }
    if ($OutFile) { [IO.File]::WriteAllText($OutFile, $owner) }
    exit 1
}
