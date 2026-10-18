# One-time preparation of the blesk server, the only step that needs the root password: makes the
# owner's and GitHub's SSH keys on this laptop, runs scripts/server-bootstrap.sh on the server as
# root with both public keys, checks that the keys work and puts GitHub's key on the clipboard
# until it is pasted.
# Running it again is safe: existing keys are kept and, once the key works, no password is asked.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-setup.ps1
#
# $env:BLESK_SERVER = '<ip>' points it at another host.
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

# Windows refuses command lines longer than 32767 characters, and the script travels inside one.
$maxRemoteCommand = 30000

function New-SshKey([string]$Path, [string]$Comment) {
    if (Test-Path -LiteralPath $Path) {
        if (-not (Test-Path -LiteralPath "$Path.pub")) {
            $public = Invoke-Native $keygen @('-y', '-f', $Path)
            if ($public.ExitCode -ne 0) { Exit-WithError "Не удалось прочитать ключ $Path." }
            [IO.File]::WriteAllLines("$Path.pub", [string[]]@($public.Lines | Select-Object -First 1))
        }
        Write-Info "Ключ $Path уже есть, беру его."
        return
    }
    # Invoke-Console keeps the empty passphrase that Windows PowerShell would drop from the call.
    if ((Invoke-Console $keygen @('-q', '-t', 'ed25519', '-N', '', '-C', $Comment, '-f', $Path)) -ne 0) {
        Exit-WithError "Не удалось создать ключ $Path."
    }
    Write-Ok "Ключ создан: $Path"
}

# The public key goes inside single quotes on the server's command line, so it must not hold one.
function Get-PublicKey([string]$Path) {
    $line = ([string](Get-Content -LiteralPath "$Path.pub" -TotalCount 1)).Trim()
    if ($line -notmatch '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-\S+) [A-Za-z0-9+/=]+( [^'']*)?$') {
        Exit-WithError "Файл $Path.pub не похож на открытый SSH-ключ."
    }
    $line
}

# The script travels inside the command itself, compressed and in base64: nothing is piped through
# the console, so line endings and encodings cannot spoil it on the way.
function ConvertTo-PackedScript([string]$Path) {
    $text = [IO.File]::ReadAllText($Path) -replace "`r", ''
    $bytes = (New-Object System.Text.UTF8Encoding $false).GetBytes($text)
    $buffer = New-Object System.IO.MemoryStream
    $gzip = New-Object System.IO.Compression.GZipStream -ArgumentList $buffer, ([IO.Compression.CompressionMode]::Compress)
    $gzip.Write($bytes, 0, $bytes.Length)
    $gzip.Close()
    [Convert]::ToBase64String($buffer.ToArray())
}

function Invoke-KeyLogin([string]$Key, [string]$Target, [string[]]$Command) {
    $options = @('-i', $Key, '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=20',
        '-o', 'StrictHostKeyChecking=accept-new', $Target)
    Invoke-Native $ssh ($options + $Command)
}

# The private key must not outlive its one paste: clipboard history (Win+V), its cloud sync and any
# app that reads the clipboard later would all get a key that opens the server. These formats keep
# it out of the history and the sync; it returns $false when only a plain copy worked.
function Copy-KeyToClipboard([string]$Text) {
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $clip = New-Object System.Windows.Forms.DataObject
        $clip.SetText($Text)
        $formats = 'ExcludeClipboardContentFromMonitorProcessing', 'CanIncludeInClipboardHistory', 'CanUploadToCloudClipboard'
        foreach ($format in $formats) {
            # A stream goes onto the clipboard as raw bytes: four zeros are the DWORD 0 that means "no".
            $clip.SetData($format, [IO.MemoryStream]::new([byte[]]@(0, 0, 0, 0)))
        }
        [System.Windows.Forms.Clipboard]::SetDataObject($clip, $true)
        return $true
    } catch {
        Write-Verbose "The clipboard cannot be told to skip its history here: $($_.Exception.Message)"
    }
    Set-Clipboard -Value $Text
    $false
}

function Clear-KeyFromClipboard {
    try {
        try {
            [System.Windows.Forms.Clipboard]::Clear()
        } catch {
            Set-Clipboard -Value ' '
        }
        $true
    } catch {
        $false
    }
}

$ssh = Find-SshTool 'ssh'
$keygen = Find-SshTool 'ssh-keygen'
$server = Get-Server
$bootstrap = Join-Path (Split-Path $PSScriptRoot -Parent) 'server-bootstrap.sh'
if (-not (Test-Path -LiteralPath $bootstrap)) {
    Exit-WithError 'Файл scripts\server-bootstrap.sh не найден. Обнови репозиторий (git pull) и запусти скрипт снова.'
}

Write-Step 'Ключи на ноутбуке'
[void][IO.Directory]::CreateDirectory($server.SshDir)
New-SshKey $server.OwnerKey 'blesk-owner'
New-SshKey $server.DeployKey 'blesk-deploy'
$ownerPublic = Get-PublicKey $server.OwnerKey
$deployPublic = Get-PublicKey $server.DeployKey

$remote = 'f=$(mktemp) && echo {0} | base64 -d | gunzip >$f && bash $f ''{1}'' ''{2}''; rc=$?; rm -f $f; exit $rc' -f `
    (ConvertTo-PackedScript $bootstrap), $ownerPublic, $deployPublic
if ($remote.Length -gt $maxRemoteCommand) {
    Exit-WithError 'scripts\server-bootstrap.sh слишком большой, чтобы передать его одной командой. Напиши мне.'
}

Write-Step "Проверяю связь с сервером $($server.Host)"
$probe = Invoke-KeyLogin $server.OwnerKey $server.Target @('true')
$keyWorks = $probe.ExitCode -eq 0
if (-not $keyWorks) {
    $probeText = $probe.Lines -join "`n"
    if ($probeText -match 'IDENTIFICATION HAS CHANGED|Host key verification failed') {
        Exit-WithError "Сервер $($server.Host) представился не тем ключом, что раньше." `
            "Если сервер переустанавливали, выполни ssh-keygen -R $($server.Host) и запусти скрипт снова." `
            'Если нет — ничего не делай и напиши мне: связь с сервером может быть подменена.'
    }
    if ($probeText -notmatch 'Permission denied') {
        Exit-WithError "Сервер $($server.Host) не отвечает:" $probe.Lines 'Проверь интернет и запусти скрипт снова.'
    }
}

Write-Step 'Настраиваю сервер'
if ($keyWorks) {
    Write-Info 'Вход по ключу уже работает, пароль не нужен.'
    $status = Invoke-Console $ssh @('-i', $server.OwnerKey, '-o', 'IdentitiesOnly=yes', $server.Target, $remote)
} else {
    Write-Info 'Сейчас сервер спросит пароль (password) root из панели Aeza.' `
        'Вставь его правой кнопкой мыши или Ctrl+V и нажми Enter. При вводе пароль не показывается — так и должно быть.'
    $status = Invoke-Console $ssh @('-o', 'PubkeyAuthentication=no', '-o', 'PreferredAuthentications=password,keyboard-interactive',
        '-o', 'NumberOfPasswordPrompts=3', '-o', 'StrictHostKeyChecking=accept-new', $server.Target, $remote)
}
if ($status -eq 255 -and -not $keyWorks) {
    Exit-WithError 'Не удалось войти на сервер. Проверь пароль root в панели Aeza (карточка сервера) и запусти скрипт снова.'
}
if ($status -ne 0) { Exit-WithError "Настройка сервера не удалась (код $status). Пришли мне скриншот этого окна." }

Write-Step 'Проверяю вход по ключам'
if ((Invoke-KeyLogin $server.OwnerKey $server.Target @('true')).ExitCode -ne 0) {
    Exit-WithError 'Сервер настроен, но твой ключ не пускает на него. Пришли мне скриншот этого окна.'
}
Write-Ok 'Твой ключ пускает на сервер: powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1'
$deployCheck = Invoke-KeyLogin $server.DeployKey "deploy@$($server.Host)" @('sudo', '-n', '-l', '/usr/local/sbin/blesk-deploy')
if ($deployCheck.ExitCode -ne 0) {
    Exit-WithError 'Сервер настроен, но ключ для GitHub не работает. Пришли мне скриншот этого окна.'
}
Write-Ok 'Ключ для GitHub пускает только к обновлению сервера.'

Write-Step 'Секреты для GitHub'
$copied = $false
$pasteHint = 'вставь из буфера обмена (Ctrl+V) целиком, от строки -----BEGIN до строки -----END'
try {
    $outOfHistory = Copy-KeyToClipboard ([IO.File]::ReadAllText($server.DeployKey))
    $copied = $true
    Write-Info 'Ключ для GitHub скопирован в буфер обмена (на экран он не выводится).'
} catch {
    Write-Info 'Скопировать в буфер обмена не получилось.'
    $pasteHint = "всё содержимое файла $($server.DeployKey) (открой его в Блокноте), от строки -----BEGIN до строки -----END"
}
# The server's own public key, as this laptop saw it while the password was typed: GitHub then
# talks only to this server and never hands the backup secrets to one that answers in its place.
$hostKey = & $keygen -F $server.Host 2>$null |
    Where-Object { $_ -notmatch '^#' -and ($_ -split ' ').Count -ge 3 } |
    ForEach-Object { $parts = $_ -split ' '; "$($parts[1]) $($parts[2])" } |
    Select-Object -First 1
if (-not $hostKey) { Exit-WithError 'Не нашёл ключ сервера в .ssh\known_hosts. Пришли мне скриншот этого окна.' }
$knownHint = $hostKey
if ($copied) { $knownHint = 'ключ сервера — скопирую в буфер обмена после VPS_SSH_KEY' }
Write-Info 'Открой github.com/gotblesk/blesk → Settings → Secrets and variables → Actions → New repository secret' `
    'и создай четыре секрета (если такой уже есть — открой его карандашом и замени значение):' `
    "  VPS_HOST         $($server.Host)" `
    '  VPS_USER         deploy' `
    "  VPS_SSH_KEY      $pasteHint" `
    "  VPS_KNOWN_HOSTS  $knownHint" `
    '' 'Никому не отправляй файлы из папки .ssh: это ключи от сервера.'
if ($copied) {
    Write-Info '' 'Когда вставишь ключ в VPS_SSH_KEY и сохранишь секрет, нажми здесь Enter:' `
        'я очищу буфер обмена, чтобы ключ в нём не остался.'
    # Ctrl+C instead of Enter must not leave the key behind either.
    try {
        [void](Read-Host)
    } finally {
        $cleared = Clear-KeyFromClipboard
    }
    if ($cleared) {
        Write-Ok 'Буфер обмена очищен.'
    } else {
        Write-Notice 'Не удалось очистить буфер обмена. Скопируй любое слово, чтобы ключ из него пропал.'
    }
    if (-not $outOfHistory) {
        Write-Notice 'Если в Windows включён журнал буфера обмена (Win+V), открой его и удали оттуда ключ.'
    }
    try {
        Set-Clipboard -Value $hostKey
        Write-Info 'Теперь в буфере обмена ключ сервера: вставь его в секрет VPS_KNOWN_HOSTS.' `
            'Он открытый, его можно не прятать:' "  $hostKey"
    } catch {
        Write-Info 'Скопируй в секрет VPS_KNOWN_HOSTS эту строку:' "  $hostKey"
    }
}
Write-Ok 'Готово. Когда создашь секреты, напиши мне: «секреты готовы».'
