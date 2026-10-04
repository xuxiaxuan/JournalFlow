param(
    [ValidateSet('start', 'stop', 'status')][string]$Action = 'start',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.runtime'
$statePath = Join-Path $runtimeDir 'processes.json'
$backendDir = Join-Path $projectRoot 'backend'
$frontendDir = Join-Path $projectRoot 'frontend'
$jarPath = Join-Path $backendDir 'target\journalflow-backend-1.0.0.jar'
$vitePath = Join-Path $frontendDir 'node_modules\vite\bin\vite.js'

function Read-State {
    if (Test-Path -LiteralPath $statePath) {
        $saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        foreach ($entry in $saved) { Write-Output $entry }
    }
}
function Find-OwnedProcess($record) {
    $process = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    if (!$process -or $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $record.started) { return $null }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($record.pid)"
    if (!$details.CommandLine -or !$details.CommandLine.Contains($record.marker)) {
        throw "Process ownership cannot be verified for PID $($record.pid); refusing to stop it."
    }
    return $process
}
function Save-State($records) {
    ConvertTo-Json -InputObject @($records) | Set-Content -LiteralPath $statePath -Encoding UTF8
}
function Wait-Ready([string]$url, $process, [switch]$Api) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw "Service exited. See logs in $runtimeDir" }
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200 -and (!$Api -or ($response.Content | ConvertFrom-Json).code -eq 0)) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Service readiness timed out: $url. See logs in $runtimeDir"
}

$records = @(Read-State)
if ($Action -eq 'stop') {
    foreach ($record in $records) {
        $process = Find-OwnedProcess $record
        if ($process) { $process.Kill(); $process.WaitForExit(10000) | Out-Null; Write-Host "Stopped $($record.name) (PID $($record.pid))." }
    }
    if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath }
    Write-Host 'Project services stopped. Database service and project data were not changed.'
    exit 0
}
if ($Action -eq 'status') {
    foreach ($record in $records) {
        $process = Find-OwnedProcess $record
        Write-Host "$($record.name): $(if ($process) { 'running' } else { 'stopped' }) (PID $($record.pid))"
    }
    if (!$records.Count) { Write-Host 'No services tracked by this launcher.' }
    exit 0
}

# Never stop an unrelated process just because it occupies a required port.
$liveRecords = @($records | Where-Object { $null -ne (Find-OwnedProcess $_) })
foreach ($port in @(8080, 5173)) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        if ($listener.OwningProcess -notin @($liveRecords | ForEach-Object { $_.pid })) {
            throw "Port $port is occupied by an untracked process. Close it manually or use the existing service."
        }
    }
}
if ($Build -and $liveRecords.Count) { throw 'Stop tracked services before using -Build.' }
if (!(Test-Path -LiteralPath $vitePath)) { throw 'Frontend dependencies missing. Run npm.cmd install in frontend first.' }
$javaExe = (Get-Command java.exe -ErrorAction Stop).Source
# Resolve java.home instead of tracking a PATH shim that launches a second JVM.
$probeInfo = New-Object System.Diagnostics.ProcessStartInfo
$probeInfo.FileName = $javaExe
$probeInfo.Arguments = '-XshowSettings:properties -version'
$probeInfo.UseShellExecute = $false
$probeInfo.CreateNoWindow = $true
$probeInfo.RedirectStandardError = $true
$probe = [System.Diagnostics.Process]::Start($probeInfo)
$javaSettings = $probe.StandardError.ReadToEnd()
$probe.WaitForExit()
$javaHomeMatch = [regex]::Match($javaSettings, '(?m)^\s*java\.home\s*=\s*(.+?)\r?$')
if (!$javaHomeMatch.Success) { throw 'Cannot resolve java.home.' }
$javaExe = Join-Path $javaHomeMatch.Groups[1].Value.Trim() 'bin\java.exe'
$nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
if ($Build -or !(Test-Path -LiteralPath $jarPath)) {
    Push-Location $backendDir
    try {
        & mvn.cmd package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }
}
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$created = @()
try {
    foreach ($service in @(
        @{ name = 'backend'; exe = $javaExe; args = @('-jar', ('"' + $jarPath + '"')); cwd = $backendDir; marker = $jarPath; url = 'http://localhost:8080/api/published?page=1&size=1' },
        @{ name = 'frontend'; exe = $nodeExe; args = @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort'); cwd = $frontendDir; marker = $vitePath; url = 'http://127.0.0.1:5173' }
    )) {
        $record = $liveRecords | Where-Object { $_.name -eq $service.name } | Select-Object -First 1
        if ($record) { $process = Find-OwnedProcess $record }
        else {
            $process = Start-Process -FilePath $service.exe -ArgumentList $service.args -WorkingDirectory $service.cwd -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir "$($service.name).out.log") -RedirectStandardError (Join-Path $runtimeDir "$($service.name).err.log")
            $record = [pscustomobject]@{ name = $service.name; pid = $process.Id; started = $process.StartTime.ToUniversalTime().Ticks.ToString(); marker = $service.marker }
            $created += $record
            $liveRecords += $record
            Save-State $liveRecords
        }
        Wait-Ready $service.url $process -Api:($service.name -eq 'backend')
        Write-Host "$($service.name) ready (PID $($process.Id))."
    }
    Write-Host 'Open http://127.0.0.1:5173 . Logs: .runtime . Stop: stop-local.cmd'
    Write-Host 'After source changes: stop-local.cmd, then start-local.cmd -Build.'
} catch {
    $failure = $_
    foreach ($record in $created) {
        $process = Find-OwnedProcess $record
        if ($process) { $process.Kill() }
    }
    Save-State @($liveRecords | Where-Object { $_.pid -notin @($created | ForEach-Object { $_.pid }) })
    throw $failure
}
