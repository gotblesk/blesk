# Helpers for the owner's laptop scripts in this folder; they dot-source it, it is never run directly.
# Written for Windows PowerShell 5.1, which every Windows 10 and 11 has: no ternary operator, no "&&"
# between commands, no "??", and the files are saved as UTF-8 with a BOM, without which 5.1 misreads
# the Russian text.

$ErrorActionPreference = 'Stop'
# Windows PowerShell redraws a progress bar for every downloaded chunk, which slows downloads tenfold.
$ProgressPreference = 'SilentlyContinue'
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
} catch {
    Write-Verbose 'No console window to switch to UTF-8: its output is Unicode already.'
}
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$BleskPackage = 'app.blesk'
# Each release carries hundreds of screenshots, so a page of a few releases is already megabytes.
$BleskReleasesApi = 'https://api.github.com/repos/gotblesk/blesk/releases?per_page=3'
$BleskAvd = 'blesk-b'
$BleskServerIp = '94.228.175.178'

# An argument may itself be a list of lines, such as a program's output.
function Write-Lines([object[]]$Lines, [string]$Color) {
    foreach ($line in ($Lines | ForEach-Object { $_ })) {
        if ($Color) { Write-Host $line -ForegroundColor $Color } else { Write-Host $line }
    }
}

# Every argument of these is printed as a line of its own.
function Write-Step {
    Write-Host ''
    Write-Lines -Lines $args -Color Cyan
}
function Write-Info { Write-Lines -Lines $args }
function Write-Ok { Write-Lines -Lines $args -Color Green }
function Write-Notice { Write-Lines -Lines $args -Color Yellow }
function Exit-WithError {
    Write-Host ''
    Write-Lines -Lines $args -Color Red
    exit 1
}

# Quotes arguments the way Windows programs split their command line back, empty ones included:
# Windows PowerShell drops an empty argument when it starts a program itself.
function ConvertTo-CommandLine([string[]]$Arguments) {
    $quoted = foreach ($argument in $Arguments) {
        # cmd.exe, which runs the SDK's .bat tools, splits arguments at ; , and = as well as at spaces:
        # sdkmanager would otherwise read system-images;android-34;… as four package names.
        if ($argument -eq '' -or $argument -match '[\s";,=]') {
            '"' + ($argument -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
        } else {
            $argument
        }
    }
    $quoted -join ' '
}

# Runs a program and collects its output and error lines, which programs like adb mix freely.
# $InputText, when given, is what the program reads as if typed.
function Invoke-Native([string]$FilePath, [string[]]$ArgumentList = @(), [string]$InputText = '') {
    $ErrorActionPreference = 'Continue'
    if ($InputText) {
        $lines = $InputText | & $FilePath @ArgumentList 2>&1 | ForEach-Object { "$_".TrimEnd("`r") }
    } else {
        $lines = & $FilePath @ArgumentList 2>&1 | ForEach-Object { "$_".TrimEnd("`r") }
    }
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Lines = @($lines) }
}

# Runs a program in this console window, so its prompts and progress reach the owner as they are.
function Invoke-Console([string]$FilePath, [string[]]$ArgumentList = @(), [string]$InputText = '') {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.Arguments = ConvertTo-CommandLine $ArgumentList
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = [bool]$InputText
    $process = [Diagnostics.Process]::Start($info)
    if ($InputText) {
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
    }
    $process.WaitForExit()
    $process.ExitCode
}

# Starts a program without a window; its output is kept to explain an early exit.
function Start-Hidden([string]$FilePath, [string[]]$ArgumentList = @()) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.Arguments = ConvertTo-CommandLine $ArgumentList
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    [Diagnostics.Process]::Start($info)
}

function Get-Timestamp { Get-Date -Format 'yyyy-MM-dd_HH-mm-ss' }

# Logs and videos for a bug report land together on the desktop, where they are easy to attach.
function Get-ReportsDir {
    $base = [Environment]::GetFolderPath('Desktop')
    if (-not $base -or -not (Test-Path -LiteralPath $base)) { $base = $HOME }
    $dir = Join-Path $base 'blesk-reports'
    [void][IO.Directory]::CreateDirectory($dir)
    $dir
}

function New-WorkDir {
    $path = Join-Path ([IO.Path]::GetTempPath()) ('blesk-' + [guid]::NewGuid().ToString('N').Substring(0, 12))
    [void][IO.Directory]::CreateDirectory($path)
    $path
}

function Find-Adb {
    $command = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Path }
    $candidates = @()
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $candidates += Join-Path $root 'platform-tools\adb.exe' }
    }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
    foreach ($dir in @('platform-tools', 'Downloads\platform-tools', 'Desktop\platform-tools')) {
        $candidates += Join-Path (Join-Path $HOME $dir) 'adb.exe'
    }
    $candidates += 'C:\platform-tools\adb.exe'
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    Exit-WithError 'adb не найден. Это программа, через которую ноутбук ставит приложения на телефон.' `
        'Открой Android Studio → Settings → Languages & Frameworks → Android SDK → вкладка SDK Tools,' `
        'отметь Android SDK Platform-Tools → Apply. Потом запусти скрипт снова.'
}

function Start-Adb([string]$Adb) {
    if ((Invoke-Native $Adb @('start-server')).ExitCode -ne 0) {
        Exit-WithError 'adb не запускается. Закрой Android Studio и другие программы для телефонов и запусти скрипт снова.'
    }
}

function Get-AdbDevices([string]$Adb) {
    foreach ($line in (Invoke-Native $Adb @('devices')).Lines) {
        $parts = $line -split "`t"
        if ($parts.Count -ge 2 -and $parts[0]) { [pscustomobject]@{ Serial = $parts[0]; State = $parts[1] } }
    }
}

function New-Target([string]$Adb, [string]$Serial, [bool]$Emulator) {
    if ($Emulator) {
        $to = 'на эмулятор «Б»'; $from = 'с эмулятора «Б»'; $on = 'на эмуляторе «Б»'
    } else {
        $to = 'на телефон'; $from = 'с телефона'; $on = 'на телефоне'
    }
    [pscustomobject]@{ Adb = $Adb; Serial = $Serial; To = $to; From = $from; On = $on }
}

function Invoke-Adb($Target, [string[]]$ArgumentList) {
    Invoke-Native $Target.Adb (@('-s', $Target.Serial) + $ArgumentList)
}

function Select-Phone([string]$Adb) {
    $devices = @(Get-AdbDevices $Adb | Where-Object { $_.Serial -notlike 'emulator-*' })
    $ready = @($devices | Where-Object { $_.State -eq 'device' })
    if ($ready.Count -eq 1) { return New-Target $Adb $ready[0].Serial $false }
    if ($ready.Count -gt 1) {
        Exit-WithError 'Подключено несколько телефонов. Оставь подключённым один и запусти скрипт снова.'
    }
    $states = ($devices | ForEach-Object { $_.State }) -join ' '
    if ($states -match 'unauthorized') {
        Exit-WithError 'Телефон подключён, но не разрешил отладку.' `
            'Разблокируй телефон и нажми «Разрешить» в окне «Разрешить отладку по USB?».' `
            'Если окна нет, отключи кабель, подключи снова и запусти скрипт ещё раз.'
    }
    if ($states -match 'offline') {
        Exit-WithError 'Телефон не отвечает. Отключи кабель, подключи снова и запусти скрипт ещё раз.'
    }
    Exit-WithError 'Телефон не найден. Подключи кабель и разреши отладку по USB на телефоне' `
        '(Настройки → Для разработчиков → Отладка по USB).' `
        'Кабель должен передавать данные: некоторые кабели только заряжают.' `
        'Если телефон всё равно не виден, поставь USB-драйвер его производителя' `
        '(для Pixel — Google USB Driver в Android Studio → SDK Tools).'
}

# The serial of the running emulator «Б», or an empty string when it is not running.
function Get-BleskEmulatorSerial([string]$Adb) {
    foreach ($device in @(Get-AdbDevices $Adb | Where-Object { $_.Serial -like 'emulator-*' })) {
        $name = (Invoke-Native $Adb @('-s', $device.Serial, 'emu', 'avd', 'name')).Lines | Select-Object -First 1
        if ($name -eq $BleskAvd) { return $device.Serial }
    }
    ''
}

function Select-Emulator([string]$Adb) {
    $serial = Get-BleskEmulatorSerial $Adb
    if (-not $serial) {
        $running = @(Get-AdbDevices $Adb | Where-Object { $_.Serial -like 'emulator-*' -and $_.State -eq 'device' })
        if ($running.Count -eq 1) { $serial = $running[0].Serial }
    }
    if (-not $serial) {
        Exit-WithError 'Эмулятор «Б» не запущен. Запусти его: powershell -ExecutionPolicy Bypass -File scripts\owner\emulator-b.ps1'
    }
    New-Target $Adb $serial $true
}

function Select-Target([switch]$Emulator) {
    $adb = Find-Adb
    Start-Adb $adb
    if ($Emulator) { return Select-Emulator $adb }
    Select-Phone $adb
}

function Get-DeviceProp($Target, [string]$Name) {
    $value = (Invoke-Adb $Target @('shell', 'getprop', $Name)).Lines | Select-Object -First 1
    [string]$value
}

# "1.0.0-rc3-build.201 (код 201)" for the installed copy, an empty string when there is none.
function Get-InstalledVersion($Target) {
    $name = ''
    $code = ''
    foreach ($line in (Invoke-Adb $Target @('shell', 'dumpsys', 'package', $BleskPackage)).Lines) {
        if (-not $name -and $line -match '^\s*versionName=(.+)$') { $name = $Matches[1].Trim() }
        if (-not $code -and $line -match 'versionCode=(\d+)') { $code = $Matches[1] }
    }
    if ($name) { return "$name (код $code)" }
    ''
}

function Read-Json([string]$Path) {
    [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}

# PowerShell 7 turns ISO dates in JSON into DateTime, Windows PowerShell keeps them as text.
function ConvertTo-UtcTime($Value) {
    if ($Value -is [DateTime]) { return $Value.ToUniversalTime() }
    $styles = [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
    [DateTime]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture, $styles)
}

function Find-Asset($Release, [string]$Name) {
    foreach ($asset in @($Release.assets)) {
        if ($asset.name -eq $Name) { return $asset }
    }
    $null
}

function Get-BodyChecksum([string]$Body) {
    $match = [regex]::Match($Body, 'SHA-256 APK:\s*`([0-9A-Fa-f]{64})`')
    if ($match.Success) { return $match.Groups[1].Value }
    ''
}

# The newest published release that has blesk.apk, from the GitHub releases list; $null if none.
function Select-LatestRelease($Releases) {
    $best = $null
    $bestTime = [DateTime]::MinValue
    foreach ($release in @($Releases)) {
        if ($release.draft -or -not $release.published_at -or -not (Find-Asset $release 'blesk.apk')) { continue }
        $time = ConvertTo-UtcTime $release.published_at
        if ($best -and $time -le $bestTime) { continue }
        $best = $release
        $bestTime = $time
    }
    if (-not $best) { return $null }
    $apk = Find-Asset $best 'blesk.apk'
    $meta = Find-Asset $best 'release.json'
    $metaUrl = ''
    if ($meta) { $metaUrl = [string]$meta.browser_download_url }
    [pscustomobject]@{
        Tag = [string]$best.tag_name
        ApkUrl = [string]$apk.browser_download_url
        ApkSize = [long]$apk.size
        MetaUrl = $metaUrl
        BodySha = Get-BodyChecksum ([string]$best.body)
    }
}

# 200 once the file is saved, the HTTP status of a refusal, or 0 when the server was not reached.
function Save-Url([string]$Url, [string]$Path, [hashtable]$Headers = @{}) {
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing -Headers $Headers
        return 200
    } catch {
        $response = $_.Exception.Response
        if ($null -ne $response) { return [int]$response.StatusCode }
        return 0
    }
}

# Adds Sha, the checksum the release build published, and Version to the newest release: both from
# release.json when the release has one, the checksum else from the line in the release notes.
function Get-LatestRelease([string]$WorkDir) {
    Write-Step 'Ищу последнюю сборку blesk на GitHub'
    $listFile = Join-Path $WorkDir 'releases.json'
    $status = Save-Url $BleskReleasesApi $listFile @{ Accept = 'application/vnd.github+json' }
    if ($status -eq 403 -or $status -eq 429) {
        Exit-WithError 'GitHub временно ограничил запросы с твоего адреса. Подожди час и запусти скрипт снова.'
    }
    if ($status -eq 0) { Exit-WithError 'Не удалось связаться с GitHub. Проверь интернет и запусти скрипт снова.' }
    if ($status -ne 200) { Exit-WithError "GitHub ответил ошибкой $status. Попробуй ещё раз через несколько минут." }
    $release = Select-LatestRelease (Read-Json $listFile)
    if (-not $release) { Exit-WithError 'На GitHub пока нет опубликованной сборки с blesk.apk.' }
    $sha = $release.BodySha
    $version = $release.Tag
    if ($release.MetaUrl) {
        $metaFile = Join-Path $WorkDir 'release.json'
        if ((Save-Url $release.MetaUrl $metaFile) -ne 200) {
            Exit-WithError "Не удалось скачать описание сборки $($release.Tag). Проверь интернет и запусти скрипт снова."
        }
        $meta = Read-Json $metaFile
        $sha = [string]$meta.apkSha256
        if ($meta.version) { $version = [string]$meta.version }
    }
    if ($sha -notmatch '^[0-9A-Fa-f]{64}$') {
        Exit-WithError "У сборки $($release.Tag) нет контрольной суммы APK. Ставить такой файл небезопасно, ничего не меняю."
    }
    Write-Info "Последняя сборка: $version"
    $release | Add-Member -NotePropertyName Sha -NotePropertyValue $sha.ToLowerInvariant()
    $release | Add-Member -NotePropertyName Version -NotePropertyValue $version
    $release
}

# The path of the downloaded APK once its SHA-256 matches the release's checksum.
function Save-VerifiedApk($Release, [string]$WorkDir) {
    $apk = Join-Path $WorkDir 'blesk.apk'
    $sizeNote = ''
    if ($Release.ApkSize -gt 0) { $sizeNote = ', {0} МБ' -f [math]::Round($Release.ApkSize / 1MB) }
    Write-Step "Скачиваю blesk.apk$sizeNote"
    Write-Info 'Это займёт минуту-две.'
    if ((Save-Url $Release.ApkUrl $apk) -ne 200) {
        Exit-WithError 'Не удалось скачать APK. Проверь интернет и запусти скрипт снова.'
    }
    $actual = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Release.Sha) {
        Remove-Item -LiteralPath $apk -Force
        Exit-WithError 'Контрольная сумма APK не совпала: файл повреждён или подменён. Ставить его нельзя.' `
            "Ожидалась:  $($Release.Sha)" "Получилась: $actual" 'Запусти скрипт ещё раз; если повторится — напиши мне.'
    }
    Write-Ok 'Контрольная сумма совпала: это тот самый файл, который собрал GitHub.'
    $apk
}

function Test-InstallSucceeded($Result) {
    @($Result.Lines | Where-Object { $_ -match '^Success' }).Count -gt 0
}

function Get-LastLine($Result) {
    $lines = @($Result.Lines | Where-Object { $_ })
    if ($lines.Count -eq 0) { return "код $($Result.ExitCode)" }
    $lines[-1]
}

function Install-Apk($Target, [string]$Apk, [bool]$Force, [string]$Rerun) {
    Write-Step "Ставлю blesk $($Target.To)"
    Write-Info "Если $($Target.On) появится вопрос об установке, разреши её."
    $result = Invoke-Adb $Target @('install', '-r', $Apk)
    if (Test-InstallSucceeded $result) { return }
    $reason = [regex]::Match(($result.Lines -join "`n"), 'INSTALL_[A-Z_]+').Value
    if ($reason -eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' -or $reason -eq 'INSTALL_FAILED_VERSION_DOWNGRADE') {
        if (-not $Force) {
            if ($reason -eq 'INSTALL_FAILED_VERSION_DOWNGRADE') {
                Write-Notice "Сейчас $($Target.On) стоит сборка blesk новее, чем последняя на GitHub."
            } else {
                Write-Notice "Сейчас $($Target.On) стоит blesk, подписанный другим ключом, и новая сборка не встаёт поверх."
            }
            Exit-WithError 'Поставить её можно, только удалив старую копию вместе с данными:' `
                'аккаунтом, чатами и ключами на этом устройстве.' 'Если они не нужны, запусти скрипт ещё раз так:' "  $Rerun"
        }
        Write-Notice "Удаляю старую копию blesk $($Target.From) вместе с её данными."
        [void](Invoke-Adb $Target @('uninstall', $BleskPackage))
        $result = Invoke-Adb $Target @('install', $Apk)
        if (Test-InstallSucceeded $result) { return }
        Exit-WithError 'Установка не удалась и после удаления старой копии:' (Get-LastLine $result)
    }
    if ($reason -eq 'INSTALL_FAILED_USER_RESTRICTED' -or $reason -eq 'INSTALL_FAILED_ABORTED') {
        Exit-WithError "Установку запретили $($Target.On). Если там было окно об установке — запусти скрипт снова и нажми «Установить»." `
            'На Xiaomi, Redmi и POCO ещё включи «Установка через USB» в разделе «Для разработчиков».'
    }
    if ($reason -eq 'INSTALL_FAILED_INSUFFICIENT_STORAGE') {
        Exit-WithError "Не хватает места $($Target.On). Освободи хотя бы 300 МБ и запусти скрипт снова."
    }
    Exit-WithError 'Установка не удалась:' (Get-LastLine $result)
}

# The whole of install-latest; emulator-b reuses it once the emulator has booted.
function Install-Latest($Target, [bool]$Force, [string]$Rerun) {
    $work = New-WorkDir
    try {
        $release = Get-LatestRelease $work
        $apk = Save-VerifiedApk $release $work
        Install-Apk $Target $apk $Force $Rerun
        $version = Get-InstalledVersion $Target
        if (-not $version) { $version = $release.Version }
        Write-Ok "Готово: $($Target.On) стоит blesk $version."
    } finally {
        Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# OpenSSH comes with Windows 10 and 11 as an optional feature; Git for Windows brings its own copy.
function Find-SshTool([string]$Name) {
    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Path }
    $candidates = @()
    if ($env:WINDIR) {
        $candidates += Join-Path $env:WINDIR "System32\OpenSSH\$Name.exe"
        $candidates += Join-Path $env:WINDIR "Sysnative\OpenSSH\$Name.exe"
    }
    if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles "Git\usr\bin\$Name.exe" }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    Exit-WithError "$Name не найден. Открой Параметры → Приложения → Дополнительные компоненты → Добавить компонент," `
        'найди «Клиент OpenSSH» и установи. Потом открой PowerShell заново и запусти скрипт снова.'
}

# BLESK_SERVER may name another host, with or without "user@" in front.
function Get-Server {
    $server = $env:BLESK_SERVER
    if (-not $server) { $server = $BleskServerIp }
    if ($server -notlike '*@*') { $server = "root@$server" }
    $sshDir = Join-Path $HOME '.ssh'
    [pscustomobject]@{
        Target = $server
        Host = $server.Substring($server.IndexOf('@') + 1)
        SshDir = $sshDir
        OwnerKey = Join-Path $sshDir 'blesk_owner'
        DeployKey = Join-Path $sshDir 'blesk_deploy'
    }
}
