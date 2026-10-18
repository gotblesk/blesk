#!/usr/bin/env bash
# One-time preparation of the blesk server, the only step that needs the root password: makes the
# owner's and GitHub's SSH keys on this laptop, runs scripts/server-bootstrap.sh on the server as
# root with both public keys, checks that the keys work and puts GitHub's key on the clipboard
# until it is pasted.
# Running it again is safe: existing keys are kept and, once the key works, no password is asked.
#
#   bash scripts/owner/server-setup.sh
#
# BLESK_SERVER=<ip> points it at another host.
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

BOOTSTRAP=$here/../server-bootstrap.sh

find_ssh
command -v ssh-keygen >/dev/null 2>&1 || fail "ssh-keygen не найден. Выполни xcode-select --install и запусти скрипт снова."
server_target
[[ -f "$BOOTSTRAP" ]] || fail "Файл scripts/server-bootstrap.sh не найден. Обнови репозиторий (git pull) и запусти скрипт снова."

# $1 is the key file, $2 its comment.
ensure_key() {
    if [[ -f "$1" ]]; then
        if [[ ! -f "$1.pub" ]]; then
            ssh-keygen -y -f "$1" >"$1.pub" || fail "Не удалось прочитать ключ $1."
        fi
        info "Ключ $1 уже есть, беру его."
        return 0
    fi
    ssh-keygen -q -t ed25519 -N '' -C "$2" -f "$1" || fail "Не удалось создать ключ $1."
    ok "Ключ создан: $1"
}

# The public key goes inside single quotes on the server's command line, so it must not hold one.
public_key() {
    local line
    line=$(tr -d '\r' <"$1.pub" | sed -n 1p)
    case $line in
        *"'"*) fail "Файл $1.pub испорчен: в нём кавычка." ;;
        ssh-ed25519\ * | ssh-rsa\ * | ecdsa-sha2-*\ *) printf '%s\n' "$line" ;;
        *) fail "Файл $1.pub не похож на открытый SSH-ключ." ;;
    esac
}

key_login() {
    local key=$1 target=$2
    shift 2
    ssh -i "$key" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=20 \
        -o StrictHostKeyChecking=accept-new "$target" "$@"
}

CLIPBOARD=''
for tool in pbcopy wl-copy xclip; do
    if command -v "$tool" >/dev/null 2>&1; then
        CLIPBOARD=$tool
        break
    fi
done

copy_to_clipboard() {
    case $CLIPBOARD in
        pbcopy) pbcopy <"$1" ;;
        wl-copy) wl-copy <"$1" ;;
        xclip) xclip -selection clipboard <"$1" ;;
        *) return 1 ;;
    esac
}

# The same as copy_to_clipboard, for text that is not in a file.
copy_text_to_clipboard() {
    local file
    file=$(mktemp)
    printf '%s' "$1" >"$file"
    copy_to_clipboard "$file"
    local status=$?
    rm -f "$file"
    return "$status"
}

# The private key must not outlive its one paste: synced devices, clipboard managers and any app
# that reads the clipboard later would all get a key that opens the server.
clear_clipboard() {
    case $CLIPBOARD in
        pbcopy) printf '' | pbcopy ;;
        wl-copy) wl-copy --clear ;;
        xclip) xclip -selection clipboard </dev/null ;;
    esac
}

step "Ключи на ноутбуке"
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
ensure_key "$OWNER_KEY" blesk-owner
ensure_key "$DEPLOY_KEY" blesk-deploy
owner_public=$(public_key "$OWNER_KEY")
deploy_public=$(public_key "$DEPLOY_KEY")

# The script travels inside the command itself, compressed and in base64: nothing is piped through
# the terminal, so line endings and encodings cannot spoil it on the way.
script=$(tr -d '\r' <"$BOOTSTRAP" | gzip -9c | base64 | tr -d '\n')
remote="f=\$(mktemp) && echo $script | base64 -d | gunzip >\$f && bash \$f '$owner_public' '$deploy_public'; rc=\$?; rm -f \$f; exit \$rc"

step "Проверяю связь с сервером $SERVER_HOST"
key_works=0
if probe=$(key_login "$OWNER_KEY" "$SERVER_TARGET" true 2>&1); then key_works=1; fi
if [[ "$key_works" -eq 0 ]]; then
    case $probe in
        *"IDENTIFICATION HAS CHANGED"* | *"Host key verification failed"*)
            fail "Сервер $SERVER_HOST представился не тем ключом, что раньше." \
                "Если сервер переустанавливали, выполни ssh-keygen -R $SERVER_HOST и запусти скрипт снова." \
                "Если нет — ничего не делай и напиши мне: связь с сервером может быть подменена."
            ;;
        *"Permission denied"*) ;;
        *) fail "Сервер $SERVER_HOST не отвечает:" "$probe" "Проверь интернет и запусти скрипт снова." ;;
    esac
fi

step "Настраиваю сервер"
status=0
if [[ "$key_works" -eq 1 ]]; then
    info "Вход по ключу уже работает, пароль не нужен."
    ssh -i "$OWNER_KEY" -o IdentitiesOnly=yes "$SERVER_TARGET" "$remote" || status=$?
else
    info "Сейчас сервер спросит пароль (password) root из панели Aeza." \
        "Вставь его и нажми Enter. При вводе пароль не показывается — так и должно быть."
    ssh -o PubkeyAuthentication=no -o PreferredAuthentications=password,keyboard-interactive \
        -o NumberOfPasswordPrompts=3 -o StrictHostKeyChecking=accept-new "$SERVER_TARGET" "$remote" || status=$?
fi
if [[ $status -eq 255 && $key_works -eq 0 ]]; then
    fail "Не удалось войти на сервер. Проверь пароль root в панели Aeza (карточка сервера) и запусти скрипт снова."
elif [[ "$status" -ne 0 ]]; then
    fail "Настройка сервера не удалась (код $status). Пришли мне скриншот этого окна."
fi

step "Проверяю вход по ключам"
key_login "$OWNER_KEY" "$SERVER_TARGET" true >/dev/null 2>&1 ||
    fail "Сервер настроен, но твой ключ не пускает на него. Пришли мне скриншот этого окна."
ok "Твой ключ пускает на сервер: bash scripts/owner/server-ssh.sh"
key_login "$DEPLOY_KEY" "deploy@$SERVER_HOST" sudo -n -l /usr/local/sbin/blesk-deploy >/dev/null 2>&1 ||
    fail "Сервер настроен, но ключ для GitHub не работает. Пришли мне скриншот этого окна."
ok "Ключ для GitHub пускает только к обновлению сервера."

step "Секреты для GitHub"
copied=0
if copy_to_clipboard "$DEPLOY_KEY"; then
    copied=1
    # Closing the window or Ctrl+C instead of Enter must not leave the key behind either.
    trap clear_clipboard EXIT
    trap 'exit 1' HUP INT TERM
    info "Ключ для GitHub скопирован в буфер обмена (на экран он не выводится)."
    paste_hint="вставь из буфера обмена (Cmd+V)"
else
    info "Скопировать в буфер обмена не получилось."
    paste_hint="всё содержимое файла $DEPLOY_KEY (открой его в текстовом редакторе)"
fi
# The server's own public key, as this laptop saw it while the password was typed: GitHub then
# talks only to this server and never hands the backup secrets to one that answers in its place.
host_key=$(ssh-keygen -F "$SERVER_HOST" 2>/dev/null | awk '!/^#/ && NF >= 3 { print $2, $3; exit }')
[[ -n "$host_key" ]] || fail "Не нашёл ключ сервера в ~/.ssh/known_hosts. Пришли мне скриншот этого окна."
if [[ "$copied" -eq 1 ]]; then
    known_hint="ключ сервера — скопирую в буфер обмена после VPS_SSH_KEY"
else
    known_hint=$host_key
fi
info "Открой github.com/gotblesk/blesk → Settings → Secrets and variables → Actions → New repository secret" \
    "и создай четыре секрета (если такой уже есть — открой его карандашом и замени значение):" \
    "  VPS_HOST         $SERVER_HOST" \
    "  VPS_USER         deploy" \
    "  VPS_SSH_KEY      $paste_hint" \
    "  VPS_KNOWN_HOSTS  $known_hint" \
    "" "Никому не отправляй файлы из папки ~/.ssh: это ключи от сервера."
if [[ "$copied" -eq 1 ]]; then
    info "" "Когда вставишь ключ в VPS_SSH_KEY и сохранишь секрет, нажми здесь Enter:" \
        "я очищу буфер обмена, чтобы ключ в нём не остался."
    read -r _ || true
    trap - EXIT HUP INT TERM
    if clear_clipboard; then
        ok "Буфер обмена очищен."
    else
        warn "Не удалось очистить буфер обмена. Скопируй любое слово, чтобы ключ из него пропал."
    fi
    if copy_text_to_clipboard "$host_key" 2>/dev/null; then
        info "Теперь в буфере обмена ключ сервера: вставь его в секрет VPS_KNOWN_HOSTS." \
            "Он открытый, его можно не прятать:" "  $host_key"
    else
        info "Скопируй в секрет VPS_KNOWN_HOSTS эту строку:" "  $host_key"
    fi
fi
ok "Готово. Когда создашь секреты, напиши мне: «секреты готовы»."
