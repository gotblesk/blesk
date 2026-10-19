# Starts the Android emulator as the second phone «Б» and installs the newest blesk build on it.
# The first run creates the emulator: a Pixel with Android 14 (API 34) and Google APIs.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\emulator-b.ps1 [-Force]
#
# -Force: as in install-latest, replace a copy of blesk that cannot be updated.
[CmdletBinding()]
param([switch]$Force)

. (Join-Path $PSScriptRoot 'lib.ps1')

$apiLevel = 34
$bootTimeout = 240
$studioSdkTools = 'Открой Android Studio → Settings → Languages & Frameworks → Android SDK → вкладка SDK Tools,'

function Find-Sdk([string]$Adb) {
    $roots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $roots += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $roots += Split-Path (Split-Path $Adb -Parent) -Parent
    foreach ($root in $roots) {
        if (-not $root) { continue }
        $emulator = Join-Path $root 'emulator\emulator.exe'
        $sdkManager = Join-Path $root 'cmdline-tools\latest\bin\sdkmanager.bat'
        if ((Test-Path -LiteralPath $emulator) -or (Test-Path -LiteralPath $sdkManager)) { return $root }
    }
    Exit-WithError 'Android SDK с эмулятором не найден.' $studioSdkTools `
        'отметь Android Emulator и Android SDK Command-line Tools (latest) → Apply. Потом запусти скрипт снова.'
}

function Test-Java([string]$Java) {
    (Test-Path -LiteralPath $Java) -and (Invoke-Native $Java @('-version')).ExitCode -eq 0
}

# sdkmanager needs Java 17 or newer, and Android Studio brings its own.
function Use-Java {
    $homes = @()
    # The installer records where Android Studio went, which may well be another drive.
    $studio = Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Android Studio' -Name Path -ErrorAction SilentlyContinue
    if ($studio -and $studio.Path) { $homes += Join-Path $studio.Path 'jbr' }
    if ($env:ProgramFiles) { $homes += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
    if ($env:LOCALAPPDATA) { $homes += Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr' }
    $homes += $env:JAVA_HOME
    foreach ($javaHome in $homes) {
        if ($javaHome -and (Test-Java (Join-Path $javaHome 'bin\java.exe'))) {
            $env:JAVA_HOME = $javaHome
            return
        }
    }
    $java = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($java -and (Test-Java $java.Path)) { return }
    Exit-WithError 'Java не найдена, а без неё не создать эмулятор. Установи Android Studio (Java входит в неё) и запусти скрипт снова.'
}

function Get-DeviceProfile([string]$AvdManager) {
    $ids = @((Invoke-Native $AvdManager @('list', 'device', '-c')).Lines | ForEach-Object { $_.Trim() })
    foreach ($id in @('pixel_7', 'pixel_6', 'pixel_5', 'pixel_4')) {
        if ($ids -contains $id) { return $id }
    }
    'pixel'
}

function Get-AvdHome {
    if ($env:ANDROID_AVD_HOME) { return $env:ANDROID_AVD_HOME }
    if ($env:ANDROID_USER_HOME) { return Join-Path $env:ANDROID_USER_HOME 'avd' }
    Join-Path $HOME '.android\avd'
}

function Set-IniValue([string]$Path, [string]$Key, [string]$Value) {
    $pattern = '^' + [regex]::Escape($Key) + '\s*='
    $lines = @([IO.File]::ReadAllLines($Path) | Where-Object { $_ -notmatch $pattern }) + "$Key=$Value"
    [IO.File]::WriteAllLines($Path, [string[]]$lines)
}

# avdmanager leaves the laptop keyboard and the graphics card unused; both make «Б» painful to use.
function Set-AvdOptions {
    $config = Join-Path (Get-AvdHome) "$BleskAvd.avd\config.ini"
    if (-not (Test-Path -LiteralPath $config)) { return }
    Set-IniValue $config 'hw.keyboard' 'yes'
    Set-IniValue $config 'hw.gpu.enabled' 'yes'
    Set-IniValue $config 'hw.gpu.mode' 'auto'
}

function New-BleskAvd([string]$Sdk) {
    $tools = Join-Path $Sdk 'cmdline-tools\latest\bin'
    $sdkManager = Join-Path $tools 'sdkmanager.bat'
    $avdManager = Join-Path $tools 'avdmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkManager) -or -not (Test-Path -LiteralPath $avdManager)) {
        Exit-WithError 'Чтобы создать эмулятор «Б», нужны Android SDK Command-line Tools.' $studioSdkTools `
            'отметь Android SDK Command-line Tools (latest) → Apply → OK. Потом запусти скрипт снова.'
    }
    Use-Java
    $image = "system-images;android-$apiLevel;google_apis;x86_64"
    $yes = "y`n" * 50
    Write-Step 'Создаю эмулятор «Б»: Pixel, Android 14'
    Write-Info 'Скачиваю Android 14 для эмулятора — около 1,5 ГБ, это может занять 10–20 минут.'
    if ((Invoke-Native $sdkManager @('--licenses') $yes).ExitCode -ne 0) {
        Exit-WithError 'Не удалось принять лицензии Android SDK. Запусти скрипт снова.'
    }
    if ((Invoke-Console $sdkManager @('--install', $image, 'emulator', 'platform-tools') $yes) -ne 0) {
        Exit-WithError 'Не удалось скачать Android 14 для эмулятора.' `
            'Проверь интернет и свободное место (нужно около 10 ГБ) и запусти скрипт снова.'
    }
    $device = Get-DeviceProfile $avdManager
    $arguments = @('create', 'avd', '--name', $BleskAvd, '--package', $image, '--device', $device)
    if ((Invoke-Console $avdManager $arguments "no`n") -ne 0) {
        Exit-WithError 'Не удалось создать эмулятор «Б». Пришли мне скриншот этого окна.'
    }
    Set-AvdOptions
    Write-Ok 'Эмулятор «Б» создан.'
}

# Without hardware acceleration an x86_64 emulator does not start at all; checked before the 1.5 GB
# download rather than after it.
function Assert-Acceleration([string]$Emulator) {
    if (-not (Test-Path -LiteralPath $Emulator)) { return }
    if ((Invoke-Native $Emulator @('-accel-check')).ExitCode -eq 0) { return }
    Exit-WithError 'Эмулятору нужно аппаратное ускорение, а в Windows оно выключено.' `
        'Пуск → набери «компоненты» → «Включение или отключение компонентов Windows» →' `
        'отметь «Платформа низкоуровневой оболочки Windows» → OK. Windows установит компонент и попросит перезагрузку.' `
        'Перезагрузи ноутбук и запусти скрипт снова.' `
        'Если не поможет: Диспетчер задач → Производительность → ЦП → строка «Виртуализация» должна быть «Включено».'
}

function Start-BleskEmulator([string]$Sdk) {
    $emulator = Join-Path $Sdk 'emulator\emulator.exe'
    $log = Join-Path ([IO.Path]::GetTempPath()) 'blesk-emulator.log'
    Write-Step 'Запускаю эмулятор «Б»'
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $env:ComSpec
    # cmd only puts the emulator's output into a file. Without a console window of its own the
    # emulator is not tied to this PowerShell window and keeps running when the window is closed.
    $info.Arguments = '/d /c ""{0}" -avd {1} -no-boot-anim >"{2}" 2>&1"' -f $emulator, $BleskAvd, $log
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    [pscustomobject]@{ Process = [Diagnostics.Process]::Start($info); Log = $log }
}

function Exit-EmulatorFailed($Started, [string]$Sdk) {
    Write-Notice 'Эмулятор закрылся, не успев загрузиться. Последние строки его журнала:'
    if (Test-Path -LiteralPath $Started.Log) { Write-Info (Get-Content -LiteralPath $Started.Log -Tail 15) }
    Write-Notice 'Проверка ускорения:'
    Write-Info (Invoke-Native (Join-Path $Sdk 'emulator\emulator.exe') @('-accel-check')).Lines
    Exit-WithError 'Если выше есть слова WHPX, Hyper-V или virtualization: Пуск → «Включение или отключение компонентов Windows» →' `
        'отметь «Платформа низкоуровневой оболочки Windows» → OK и перезагрузи ноутбук.' `
        'Ещё проверь, что виртуализация включена: Диспетчер задач → Производительность → ЦП → «Виртуализация: Включено».' `
        "Если не поможет, пришли мне скриншот этого окна. Журнал эмулятора целиком: $($Started.Log)"
}

function Wait-ForBoot([string]$Adb, $Started, [string]$Sdk) {
    Write-Info 'Жду, пока Android загрузится (до 4 минут).'
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $serial = ''
    $note = 15
    while ($true) {
        if ($Started -and $Started.Process.HasExited) { Exit-EmulatorFailed $Started $Sdk }
        if (-not $serial) { $serial = Get-BleskEmulatorSerial $Adb }
        if ($serial) {
            $booted = (Invoke-Native $Adb @('-s', $serial, 'shell', 'getprop', 'sys.boot_completed')).Lines -join ''
            if ($booted.Trim() -eq '1') { break }
        }
        $elapsed = [int]$clock.Elapsed.TotalSeconds
        if ($elapsed -ge $bootTimeout) {
            Exit-WithError 'Эмулятор не загрузился за 4 минуты.' `
                'Дождись, пока на его экране появится рабочий стол, и запусти скрипт снова: он просто поставит blesk.'
        }
        if ($elapsed -ge $note) {
            Write-Info "…прошло $elapsed с"
            $note += 15
        }
        Start-Sleep -Seconds 3
    }
    $target = New-Target $Adb $serial $true
    [void](Invoke-Adb $target @('shell', 'wm', 'dismiss-keyguard'))
    Write-Ok 'Эмулятор «Б» загрузился.'
    $target
}

$adb = Find-Adb
Start-Adb $adb
$sdk = Find-Sdk $adb
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$started = $null
if (Get-BleskEmulatorSerial $adb) {
    Write-Step 'Эмулятор «Б» уже запущен'
} else {
    Assert-Acceleration $emulator
    $avds = @()
    if (Test-Path -LiteralPath $emulator) { $avds = @((Invoke-Native $emulator @('-list-avds')).Lines) }
    if ($avds -notcontains $BleskAvd) { New-BleskAvd $sdk }
    if (-not (Test-Path -LiteralPath $emulator)) { Exit-WithError 'Эмулятор не установился. Запусти скрипт снова.' }
    Assert-Acceleration $emulator
    $started = Start-BleskEmulator $sdk
}
$target = Wait-ForBoot $adb $started $sdk
Install-Latest $target $Force.IsPresent 'powershell -ExecutionPolicy Bypass -File scripts\owner\emulator-b.ps1 -Force'
Write-Info 'Эмулятор можно не закрывать между проверками. Выключить его — закрыть окно эмулятора.'
