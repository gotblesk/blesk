# Records the phone's screen (or the emulator «Б»'s) into a video file for a bug report.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\screenrecord.ps1 [-Emulator]
[CmdletBinding()]
param([switch]$Emulator)

. (Join-Path $PSScriptRoot 'lib.ps1')

$remoteFile = '/sdcard/blesk-record.mp4'
$limitSeconds = 180

# Enter and Ctrl+C both stop the recording, so the video is kept either way.
function Wait-ForStop([Diagnostics.Process]$Recorder) {
    try {
        [Console]::TreatControlCAsInput = $true
        while (-not $Recorder.HasExited) {
            if ([Console]::KeyAvailable) {
                $key = [Console]::ReadKey($true)
                $ctrlC = $key.Key -eq 'C' -and ($key.Modifiers -band [ConsoleModifiers]::Control)
                if ($key.Key -eq 'Enter' -or $ctrlC) { return }
            }
            Start-Sleep -Milliseconds 200
        }
    } catch {
        Write-Verbose 'The keyboard cannot be polled here, so a whole line is awaited.'
        [void](Read-Host)
    } finally {
        try { [Console]::TreatControlCAsInput = $false } catch { Write-Verbose 'No console to restore.' }
    }
}

function Test-RecordingOnDevice($Target) {
    $lines = (Invoke-Adb $Target @('shell', 'pidof', 'screenrecord')).Lines
    [bool](($lines -join '').Trim())
}

$target = Select-Target -Emulator:$Emulator
$file = Join-Path (Get-ReportsDir) ('blesk-video-{0}.mp4' -f (Get-Timestamp))

[void](Invoke-Adb $target @('shell', 'rm', '-f', $remoteFile))
Write-Step "Записываю экран $($target.From)"
$recorder = Start-Hidden $target.Adb @('-s', $target.Serial, 'shell', 'screenrecord', '--time-limit', "$limitSeconds", $remoteFile)
$clock = [Diagnostics.Stopwatch]::StartNew()
Start-Sleep -Seconds 1
if ($recorder.HasExited) {
    Write-Info $recorder.StandardError.ReadToEnd() $recorder.StandardOutput.ReadToEnd()
    Exit-WithError 'Запись не началась. Разблокируй экран и запусти скрипт снова.' `
        'Если не поможет, пришли мне скриншот этого окна.'
}

Write-Info 'Запись идёт. Нажми Enter, чтобы остановить (не дольше 3 минут)'
Wait-ForStop $recorder
if ($recorder.HasExited) {
    if ($clock.Elapsed.TotalSeconds -ge $limitSeconds - 5) {
        Write-Info 'Прошло 3 минуты, запись остановилась сама.'
    } else {
        Write-Notice 'Запись прервалась раньше времени. Сохраняю то, что успело записаться.'
    }
}

Write-Step 'Останавливаю запись'
# SIGINT makes screenrecord finish the file properly; older toybox builds lack pkill -INT.
[void](Invoke-Adb $target @('shell', 'pkill -INT screenrecord || kill -INT $(pidof screenrecord)'))
if (-not $recorder.WaitForExit(15000)) { $recorder.Kill() }
for ($waited = 0; $waited -lt 10 -and (Test-RecordingOnDevice $target); $waited++) { Start-Sleep -Seconds 1 }

if ((Invoke-Adb $target @('pull', $remoteFile, $file)).ExitCode -ne 0) {
    Exit-WithError "Не удалось забрать видео $($target.From). Отключи кабель, подключи снова и запусти скрипт ещё раз."
}
[void](Invoke-Adb $target @('shell', 'rm', '-f', $remoteFile))

Write-Ok "Видео сохранено: $file"
Write-Info 'Приложи его к отчёту об ошибке вместе с журналом (logs).'
