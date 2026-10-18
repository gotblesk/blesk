# Saves blesk's log from the phone (or from the emulator «Б») into a file for a bug report: device
# and app version on top, then the app's own lines and its crashes.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\logs.ps1 [-Emulator]
[CmdletBinding()]
param([switch]$Emulator)

. (Join-Path $PSScriptRoot 'lib.ps1')

# threadtime lines are "date time pid tid level tag: message".
$threadtime = New-Object regex '^\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(\d+)\s+\d+\s+[VDIWEFA]\s+(.*?)\s*: '
$startedProcess = New-Object regex 'Start proc (\d+):app\.blesk[:/]'

# Besides the app's current process the log keeps earlier ones: those that ActivityManager started
# as app.blesk and those that crashed. ActivityManager's own lines about the app tell when it was
# killed or stopped responding.
function Select-AppLines([string[]]$Lines, [string[]]$CurrentPids) {
    $pids = @{}
    foreach ($id in $CurrentPids) { $pids[$id] = $true }
    foreach ($line in $Lines) {
        $started = $startedProcess.Match($line)
        if ($started.Success) { $pids[$started.Groups[1].Value] = $true }
        $entry = $threadtime.Match($line)
        if ($entry.Success -and $entry.Groups[2].Value -eq 'AndroidRuntime' -and $line.Contains('app.blesk')) {
            $pids[$entry.Groups[1].Value] = $true
        }
    }
    foreach ($line in $Lines) {
        $entry = $threadtime.Match($line)
        if (-not $entry.Success) { continue }
        $tag = $entry.Groups[2].Value
        if ($tag.StartsWith('blesk') -or $pids.ContainsKey($entry.Groups[1].Value) -or
            ($tag -eq 'ActivityManager' -and $line.Contains('app.blesk'))) {
            $line
        }
    }
}

# A crash entry is written by one process (the app itself, or crash_dump for a native crash), so
# every line of a process that mentions the app belongs to the app's crashes.
function Select-CrashLines([string[]]$Lines) {
    $pids = @{}
    foreach ($line in $Lines) {
        $entry = $threadtime.Match($line)
        if ($entry.Success -and $line.Contains('app.blesk')) { $pids[$entry.Groups[1].Value] = $true }
    }
    foreach ($line in $Lines) {
        $entry = $threadtime.Match($line)
        if ($entry.Success -and $pids.ContainsKey($entry.Groups[1].Value)) { $line }
    }
}

$target = Select-Target -Emulator:$Emulator
$file = Join-Path (Get-ReportsDir) ('blesk-log-{0}.txt' -f (Get-Timestamp))

Write-Step "Сохраняю журнал blesk $($target.From)"
$appPids = @(((Invoke-Adb $target @('shell', 'pidof', $BleskPackage)).Lines -join ' ') -split '\s+' | Where-Object { $_ -match '^\d+$' })
$version = Get-InstalledVersion $target
$main = Invoke-Adb $target @('logcat', '-d', '-v', 'threadtime', '-b', 'main', '-b', 'system')
if ($main.ExitCode -ne 0) {
    Exit-WithError "Не удалось прочитать журнал $($target.From). Отключи кабель, подключи снова и запусти скрипт ещё раз."
}
$crash = Invoke-Adb $target @('logcat', '-d', '-v', 'threadtime', '-b', 'crash')
$appLines = @(Select-AppLines $main.Lines $appPids)
$crashLines = @(Select-CrashLines $crash.Lines)

if (-not $version) { $version = 'не установлен' }
$processNote = 'не запущен'
if ($appPids.Count -gt 0) { $processNote = $appPids -join ' ' }
if ($appLines.Count -eq 0) { $appLines = @('(пусто)') }
if ($crashLines.Count -eq 0) { $crashLines = @('(пусто)') }
$device = '{0} {1} (Android {2}, API {3})' -f (Get-DeviceProp $target 'ro.product.manufacturer'),
    (Get-DeviceProp $target 'ro.product.model'), (Get-DeviceProp $target 'ro.build.version.release'),
    (Get-DeviceProp $target 'ro.build.version.sdk')
$report = @(
    'Журнал blesk для отчёта об ошибке'
    'Сохранён: {0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
    "Устройство: $device"
    "blesk: $version"
    "Процесс blesk: $processNote"
    ''
    '===== Журнал (main, system) ====='
) + $appLines + @('', '===== Сбои (crash) =====') + $crashLines
[IO.File]::WriteAllLines($file, [string[]]$report, (New-Object System.Text.UTF8Encoding $false))

Write-Ok "Журнал сохранён: $file"
Write-Info 'Приложи этот файл к отчёту об ошибке вместе со скриншотом или видео (screenrecord).'
