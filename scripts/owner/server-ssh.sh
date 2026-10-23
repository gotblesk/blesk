#!/usr/bin/env bash
# Opens the blesk server from the laptop with the owner's key that server-setup made.
#
#   bash scripts/owner/server-ssh.sh               a shell on the server
#   bash scripts/owner/server-ssh.sh invites       registration invite links
#   bash scripts/owner/server-ssh.sh invites add 5 five more links
#   bash scripts/owner/server-ssh.sh official egor makes @blesk the official channel, owned by @egor
#   bash scripts/owner/server-ssh.sh status        containers and the health check
#   bash scripts/owner/server-ssh.sh logs          the last 200 lines of the server's log
#
# BLESK_SERVER=<ip> points it at another host.
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

HEALTH_URL=https://api.blesk.su/health
STACK='cd /opt/blesk/deploy && docker compose'
CLI='exec -T server /opt/blesk-server/bin/blesk-server'

usage() {
    fail "Неизвестная команда: $*" \
        "Можно: без команды (зайти на сервер), invites, invites add <число от 1 до 50>, official <имя пользователя>, status, logs."
}

find_ssh
server_target
if [[ ! -f "$OWNER_KEY" ]]; then
    fail "Нет ключа для входа на сервер ($OWNER_KEY)." \
        "Сначала один раз запусти настройку: bash scripts/owner/server-setup.sh"
fi

open_shell() {
    ssh -i "$OWNER_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new "$SERVER_TARGET"
}

# Unlike the shell, a command must not stop at a password prompt: it would hide behind the output.
run_on_server() {
    ssh -i "$OWNER_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes "$SERVER_TARGET" "$@"
}

unreachable() {
    fail "Не удалось выполнить команду на сервере $SERVER_HOST (код $1)." \
        "Проверь интернет. Если сервер просит пароль или пишет Permission denied — запусти bash scripts/owner/server-setup.sh"
}

show_invites() {
    local output links status=0
    step "Спрашиваю у сервера ссылки-приглашения"
    output=$(run_on_server "$STACK $CLI invites${1:+ add $1}" 2>&1) || status=$?
    links=$(grep -E '^https://blesk\.su/i/[A-Za-z0-9]+$' <<<"$(tr -d '\r' <<<"$output")" || true)
    if [[ $status -ne 0 || -z $links ]]; then
        info "$output"
        unreachable "$status"
    fi
    ok "Ссылки на регистрацию в blesk. Каждая срабатывает один раз:"
    info "$links"
    info "" "Отправь ссылку тому, кого зовёшь в blesk: по ней можно установить приложение и зарегистрироваться."
}

# The server answers 3 when there is no such account and 4 when another account holds @blesk.
make_official() {
    local output status=0
    step "Делаю канал @blesk официальным, владелец — @$1"
    output=$(run_on_server "$STACK $CLI official $1" 2>&1) || status=$?
    case $status in
        0)
            ok "Канал @blesk готов: он официальный, а ты его владелец."
            info "Писать в канал — из приложения, как в любой свой канал."
            ;;
        3)
            fail "На сервере нет аккаунта @$1." \
                "Сначала зарегистрируйся в приложении под этим именем, потом повтори команду."
            ;;
        4)
            fail "Адрес @blesk уже занят каналом или комнатой другого аккаунта." \
                "Удали этот канал или комнату в приложении владельца, потом повтори команду."
            ;;
        *)
            info "$output"
            unreachable "$status"
            ;;
    esac
}

check_health() {
    local body code
    step "Проверяю $HEALTH_URL"
    body=$(curl -sS -m 15 -w '\n%{http_code}' "$HEALTH_URL" 2>&1) || {
        warn "Сервер не отвечает: $(sed -n 1p <<<"$body")"
        return 1
    }
    code=$(sed -n '$p' <<<"$body")
    case $code in
        200) ok "Сервер отвечает, база данных доступна." ;;
        503)
            warn "Сервер работает, но база данных недоступна."
            return 1
            ;;
        *)
            warn "Сервер ответил кодом $code."
            return 1
            ;;
    esac
}

case ${1:-} in
    '')
        [[ $# -le 1 ]] || usage "$*"
        step "Захожу на сервер $SERVER_HOST. Выйти — команда exit"
        open_shell || exit $?
        ;;
    invites)
        if [[ $# -eq 1 ]]; then
            show_invites ''
        elif [[ $# -eq 3 && $2 == add && $3 =~ ^[0-9]{1,2}$ ]] && ((10#$3 >= 1 && 10#$3 <= 50)); then
            show_invites "$((10#$3))"
        else
            usage "$*"
        fi
        ;;
    official)
        [[ $# -eq 2 ]] || usage "$*"
        # Checked here because the name goes into a command line on the server.
        name=$(tr '[:upper:]' '[:lower:]' <<<"${2#@}")
        if [[ ${#name} -lt 3 || ${#name} -gt 32 || ! $name =~ ^[a-z][a-z0-9]*([._][a-z0-9]+)*$ ]]; then
            fail "«$2» — не имя пользователя." "Имя пишется латиницей, как в приложении: например egor."
        fi
        make_official "$name"
        ;;
    status)
        [[ $# -eq 1 ]] || usage "$*"
        step "Контейнеры на сервере $SERVER_HOST"
        status=0
        run_on_server "$STACK ps" || status=$?
        if [[ "$status" -ne 0 ]]; then unreachable "$status"; fi
        check_health || exit 1
        ;;
    logs)
        [[ $# -eq 1 ]] || usage "$*"
        step "Последние 200 строк журнала сервера"
        status=0
        run_on_server "$STACK logs --no-color --tail 200 server" || status=$?
        if [[ "$status" -ne 0 ]]; then unreachable "$status"; fi
        ;;
    *) usage "$*" ;;
esac
