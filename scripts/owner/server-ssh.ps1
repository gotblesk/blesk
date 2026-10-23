# Opens the blesk server from the laptop with the owner's key that server-setup made.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1                a shell on the server
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 invites        registration invite links
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 invites add 5  five more links
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 official egor  makes @blesk the official channel, owned by @egor
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 status         containers and the health check
#   powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 logs           the last 200 lines of the server's log
#
# $env:BLESK_SERVER = '<ip>' points it at another host.
param(
    [Parameter(Position = 0)][string]$Action = '',
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest = @()
)

. (Join-Path $PSScriptRoot 'lib.ps1')

$healthUrl = 'https://api.blesk.su/health'
$stack = 'cd /opt/blesk/deploy && docker compose'
$cli = 'exec -T server /opt/blesk-server/bin/blesk-server'
$extra = @($Rest | Where-Object { $_ })

function Exit-WithUsage {
    Exit-WithError "Неизвестная команда: $(@($Action) + $extra -join ' ')" `
        'Можно: без команды (зайти на сервер), invites, invites add <число от 1 до 50>, official <имя пользователя>, status, logs.'
}

function Get-SshArguments([switch]$Batch) {
    $arguments = @('-i', $server.OwnerKey, '-o', 'IdentitiesOnly=yes', '-o', 'StrictHostKeyChecking=accept-new')
    if ($Batch) { $arguments += @('-o', 'BatchMode=yes') }
    $arguments + $server.Target
}

function Exit-Unreachable([int]$Code) {
    Exit-WithError "Не удалось выполнить команду на сервере $($server.Host) (код $Code)." `
        'Проверь интернет. Если сервер просит пароль или пишет Permission denied — запусти scripts\owner\server-setup.ps1'
}

function Show-Invites([string]$Count) {
    Write-Step 'Спрашиваю у сервера ссылки-приглашения'
    $command = "$stack $cli invites"
    if ($Count) { $command += " add $Count" }
    $result = Invoke-Native $ssh ((Get-SshArguments -Batch) + $command)
    $links = @($result.Lines | Where-Object { $_ -match '^https://blesk\.su/i/[A-Za-z0-9]+$' })
    if ($result.ExitCode -ne 0 -or $links.Count -eq 0) {
        Write-Info $result.Lines
        Exit-Unreachable $result.ExitCode
    }
    Write-Ok 'Ссылки на регистрацию в blesk. Каждая срабатывает один раз:'
    Write-Info $links
    Write-Info '' 'Отправь ссылку тому, кого зовёшь в blesk: по ней можно установить приложение и зарегистрироваться.'
}

# The server answers 3 when there is no such account, 4 when another account holds @blesk and 5 when it is this account's room.
function Set-Official([string]$Name) {
    Write-Step "Делаю канал @blesk официальным, владелец — @$Name"
    $result = Invoke-Native $ssh ((Get-SshArguments -Batch) + "$stack $cli official $Name")
    switch ($result.ExitCode) {
        0 {
            Write-Ok 'Канал @blesk готов: он официальный, а ты его владелец.'
            Write-Info 'Писать в канал — из приложения, как в любой свой канал.'
        }
        3 {
            Exit-WithError "На сервере нет аккаунта @$Name." `
                'Сначала зарегистрируйся в приложении под этим именем, потом повтори команду.'
        }
        4 {
            Exit-WithError 'Адрес @blesk уже занят каналом или комнатой другого аккаунта.' `
                'Удали этот канал или комнату в приложении владельца, потом повтори команду.'
        }
        5 {
            Exit-WithError "Адрес @blesk занят комнатой @$Name, а официальным может быть только канал." `
                'Удали эту комнату в приложении, потом повтори команду.'
        }
        default {
            Write-Info $result.Lines
            Exit-Unreachable $result.ExitCode
        }
    }
}

function Test-Health {
    Write-Step "Проверяю $healthUrl"
    try {
        [void](Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 15)
        Write-Ok 'Сервер отвечает, база данных доступна.'
        return $true
    } catch {
        $status = 0
        if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq 503) {
            Write-Notice 'Сервер работает, но база данных недоступна.'
        } elseif ($status -gt 0) {
            Write-Notice "Сервер ответил кодом $status."
        } else {
            Write-Notice "Сервер не отвечает: $($_.Exception.Message)"
        }
        return $false
    }
}

$ssh = Find-SshTool 'ssh'
$server = Get-Server
if (-not (Test-Path -LiteralPath $server.OwnerKey)) {
    Exit-WithError "Нет ключа для входа на сервер ($($server.OwnerKey))." `
        'Сначала один раз запусти настройку: powershell -ExecutionPolicy Bypass -File scripts\owner\server-setup.ps1'
}

switch ($Action) {
    '' {
        if ($extra.Count -gt 0) { Exit-WithUsage }
        Write-Step "Захожу на сервер $($server.Host). Выйти — команда exit"
        exit (Invoke-Console $ssh (Get-SshArguments))
    }
    'invites' {
        if ($extra.Count -eq 0) {
            Show-Invites ''
        } elseif ($extra.Count -eq 2 -and $extra[0] -eq 'add' -and $extra[1] -match '^\d{1,2}$' -and
            [int]$extra[1] -ge 1 -and [int]$extra[1] -le 50) {
            Show-Invites ([int]$extra[1]).ToString()
        } else {
            Exit-WithUsage
        }
    }
    'official' {
        if ($extra.Count -ne 1) { Exit-WithUsage }
        # Validated here because the name goes into a command line on the server.
        $name = ($extra[0] -replace '^@', '').ToLowerInvariant()
        if ($name.Length -lt 3 -or $name.Length -gt 32 -or $name -cnotmatch '^[a-z][a-z0-9]*([._][a-z0-9]+)*$') {
            Exit-WithError "«$($extra[0])» — не имя пользователя." 'Имя пишется латиницей, как в приложении: например egor.'
        }
        Set-Official $name
    }
    'status' {
        if ($extra.Count -gt 0) { Exit-WithUsage }
        Write-Step "Контейнеры на сервере $($server.Host)"
        $code = Invoke-Console $ssh ((Get-SshArguments -Batch) + "$stack ps")
        if ($code -ne 0) { Exit-Unreachable $code }
        if (-not (Test-Health)) { exit 1 }
    }
    'logs' {
        if ($extra.Count -gt 0) { Exit-WithUsage }
        Write-Step 'Последние 200 строк журнала сервера'
        $code = Invoke-Console $ssh ((Get-SshArguments -Batch) + "$stack logs --no-color --tail 200 server")
        if ($code -ne 0) { Exit-Unreachable $code }
    }
    default { Exit-WithUsage }
}
