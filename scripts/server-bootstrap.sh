#!/usr/bin/env bash
# Prepares a fresh blesk host for key-only SSH and for deploys from GitHub Actions. Run it once as
# root (the only time the password from the hosting panel is needed); running it again is safe.
#
#   bash server-bootstrap.sh '<owner public key line>' '<deploy public key line>'
#
# The owner's key opens root. The deploy key opens only the user "deploy", which has no password
# and may run nothing as root but /usr/local/sbin/blesk-deploy, that is scripts/deploy.sh, and pass
# it nothing but the deploy's own settings. Password login stays on here: scripts/deploy.sh turns it
# off (BLESK_SSH_KEYS_ONLY=1) after checking that root and deploy both have keys, so nobody gets
# locked out.
set -euo pipefail

DEPLOY_USER=deploy
ROOT_KEYS=/root/.ssh/authorized_keys
WRAPPER=/usr/local/sbin/blesk-deploy
SUDOERS=/etc/sudoers.d/blesk-deploy
HOST_KEY=/etc/ssh/ssh_host_ed25519_key.pub
# The only variables sudo hands over to the deploy; scripts/ci/remote-deploy.sh exports these names.
DEPLOY_VARS="BLESK_BRANCH BLESK_COMMIT BLESK_DOMAIN BLESK_SITE_DOMAIN BLESK_RESTORE BLESK_SSH_KEYS_ONLY \
BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY BACKUP_PASSWORD"

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() {
    printf '\033[31mОшибка: %s\033[0m\n' "$*" >&2
    exit 1
}

# Pasting from a phone or Windows adds spaces and a carriage return around the line.
normalize_key() {
    local line=${1//$'\r'/}
    line=${line#"${line%%[![:space:]]*}"}
    printf '%s' "${line%"${line##*[![:space:]]}"}"
}

# The base64 part identifies a key; the comment after it may differ between copies.
key_blob() {
    local _type blob
    read -r _type blob _ <<<"$1"
    printf '%s' "$blob"
}

# A key blob is a run of length-prefixed fields that starts with the key type and uses the blob up
# exactly, so a line cut short, glued to another or labelled with the wrong type fails here.
blob_matches_type() {
    local type=$1 hex prefix i=0 fields=0
    hex=$(printf '%s' "$2" | base64 -d 2>/dev/null | od -An -v -tx1 | tr -d ' \n')
    prefix=$(printf '%08x' "${#type}")$(printf '%s' "$type" | od -An -v -tx1 | tr -d ' \n')
    [[ ${hex:0:${#prefix}} == "$prefix" ]] || return 1
    while ((i + 8 <= ${#hex})); do
        i=$((i + 8 + 2 * 16#${hex:i:8}))
        fields=$((fields + 1))
    done
    ((i == ${#hex} && fields >= 2))
}

# A public key line as ssh-keygen writes it: type, base64 blob, optional comment. Options in front
# (command=, from=) are refused, and so is anything else pasted by mistake, a private key included.
is_public_key() {
    local line=$1 type blob _comment
    [[ $line != *$'\n'* ]] || return 1
    read -r type blob _comment <<<"$line"
    case $type in
        ssh-ed25519 | ssh-rsa | ecdsa-sha2-nistp256 | ecdsa-sha2-nistp384 | ecdsa-sha2-nistp521) ;;
        sk-ssh-ed25519@openssh.com | sk-ecdsa-sha2-nistp256@openssh.com) ;;
        *) return 1 ;;
    esac
    [[ $blob =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || return 1
    blob_matches_type "$type" "$blob" || return 1
    if command -v ssh-keygen >/dev/null; then
        ssh-keygen -l -f - <<<"$line" >/dev/null 2>&1
    fi
}

has_key() {
    local file=$1 blob=$2
    [[ -f $file ]] && awk -v blob="$blob" '{ for (i = 1; i <= NF; i++) if ($i == blob) found = 1 } END { exit !found }' "$file"
}

ensure_tools() {
    local missing=()
    command -v sudo >/dev/null || missing+=(sudo)
    command -v curl >/dev/null || missing+=(curl)
    if ((${#missing[@]} == 0)); then return 0; fi
    say "Устанавливаю ${missing[*]}"
    DEBIAN_FRONTEND=noninteractive apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}" >/dev/null
}

authorize_owner() {
    local key=$1 deploy_key=$2 blob tmp
    say "Ключ владельца для root"
    install -d -m 700 "$(dirname "$ROOT_KEYS")"
    touch "$ROOT_KEYS"
    chmod 600 "$ROOT_KEYS"
    blob=$(key_blob "$key")
    if has_key "$ROOT_KEYS" "$blob"; then
        echo "Уже добавлен"
    else
        # Without a final newline the new key would be glued to the last line of the file.
        if [[ -s $ROOT_KEYS && -n $(tail -c 1 "$ROOT_KEYS") ]]; then echo >>"$ROOT_KEYS"; fi
        printf '%s\n' "$key" >>"$ROOT_KEYS"
        echo "Добавлен"
    fi
    # The deploy key must reach root only through blesk-deploy, never by logging in as root.
    blob=$(key_blob "$deploy_key")
    if has_key "$ROOT_KEYS" "$blob"; then
        tmp=$(mktemp)
        awk -v blob="$blob" '{ for (i = 1; i <= NF; i++) if ($i == blob) next } { print }' "$ROOT_KEYS" >"$tmp"
        cat "$tmp" >"$ROOT_KEYS"
        rm -f "$tmp"
        echo "Ключ деплоя убран из ключей root: он работает только через пользователя $DEPLOY_USER"
    fi
}

prepare_deploy_user() {
    local key=$1 home group tmp
    say "Пользователь $DEPLOY_USER"
    if id "$DEPLOY_USER" >/dev/null 2>&1; then
        echo "Уже есть"
    else
        useradd --create-home --shell /bin/bash "$DEPLOY_USER"
        echo "Создан"
    fi
    usermod --shell /bin/bash "$DEPLOY_USER"
    # "*" rather than the "!" of passwd -l: no password matches either, but with UsePAM off sshd
    # also refuses key logins to accounts whose password field starts with "!".
    usermod --password '*' "$DEPLOY_USER"
    home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
    group=$(id -gn "$DEPLOY_USER")
    install -d -m 700 -o "$DEPLOY_USER" -g "$group" "$home/.ssh"
    tmp=$(mktemp)
    # "restrict": no forwarding of ports or agents and no terminal, a command over SSH is all it gets.
    printf 'restrict %s\n' "$key" >"$tmp"
    install -m 600 -o "$DEPLOY_USER" -g "$group" "$tmp" "$home/.ssh/authorized_keys"
    rm -f "$tmp"
    echo "Вход — только по ключу деплоя"
}

install_wrapper() {
    local tmp
    say "Команда $WRAPPER"
    tmp=$(mktemp)
    cat >"$tmp" <<'EOF'
#!/bin/bash
# Installed by scripts/server-bootstrap.sh. The deploy user may run this, and only this, as root
# (see /etc/sudoers.d/blesk-deploy), with only the deploy's settings left in the environment.
set -euo pipefail
# sudo drops these already; they would swap the code that gets installed, so they stay dropped even
# if its list of kept variables is ever widened.
unset BLESK_REPO BLESK_DIR BLESK_BUILD_HERE
branch=${BLESK_BRANCH:-master}
# A branch name and never a path or an option: "..", a leading "/" or "-" could point the download
# or git somewhere else.
if [[ ! $branch =~ ^[A-Za-z0-9._/-]+$ || $branch == *..* || $branch == [/-]* ]]; then
    echo "blesk-deploy: недопустимое имя ветки" >&2
    exit 2
fi
script=/opt/blesk/scripts/deploy.sh
if [[ -f $script ]]; then
    exec bash "$script"
fi
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
# refs/heads/ makes GitHub read the name as a branch of this repository, never as a commit SHA,
# which could also name a commit pushed to a fork.
curl -fsSL "https://raw.githubusercontent.com/gotblesk/blesk/refs/heads/$branch/scripts/deploy.sh" -o "$tmp"
bash "$tmp"
EOF
    install -m 755 -o root -g root "$tmp" "$WRAPPER"
    rm -f "$tmp"
    echo "Установлена"
}

install_sudoers() {
    local tmp
    say "Права $DEPLOY_USER в sudo"
    tmp=$(mktemp)
    cat >"$tmp" <<EOF
# Installed by scripts/server-bootstrap.sh: GitHub Actions log in as $DEPLOY_USER and may run the
# deploy as root and nothing else. The environment is reset and keeps only the deploy's settings, so
# no variable (BASH_ENV, LD_PRELOAD, BLESK_REPO) can make bash, the loader or deploy.sh run other code.
Defaults!$WRAPPER env_reset, env_keep = "$DEPLOY_VARS"
$DEPLOY_USER ALL=(root) NOPASSWD: $WRAPPER
EOF
    visudo -cqf "$tmp" || die "visudo отверг правило для $DEPLOY_USER"
    install -m 440 -o root -g root "$tmp" "$SUDOERS"
    rm -f "$tmp"
    if ! visudo -cq; then
        rm -f "$SUDOERS"
        die "после добавления правила sudo перестал проверяться, правило убрано"
    fi
    grep -Eq '^[@#]includedir[[:space:]]+/etc/sudoers.d' /etc/sudoers ||
        die "/etc/sudoers не читает /etc/sudoers.d, правило для $DEPLOY_USER не подействует"
    echo "Разрешено: sudo -n $WRAPPER, настройки деплоя — в переменных окружения"
}

# GitHub Actions accept the server only by this key (the VPS_KNOWN_HOSTS secret), so a stranger
# answering in its place on the network gets no secrets. The key is public.
print_host_key() {
    [[ -f $HOST_KEY ]] || return 0
    say "Ключ сервера для секрета VPS_KNOWN_HOSTS в GitHub"
    cut -d' ' -f1,2 "$HOST_KEY"
}

main() {
    [[ $EUID -eq 0 ]] || die "запусти от root"
    (($# == 2)) || die "нужны два ключа: bash server-bootstrap.sh '<ключ владельца>' '<ключ деплоя>'"
    local owner deploy
    owner=$(normalize_key "$1")
    deploy=$(normalize_key "$2")
    is_public_key "$owner" || die "первый аргумент не похож на открытый SSH-ключ (одна строка из blesk_owner.pub)"
    is_public_key "$deploy" || die "второй аргумент не похож на открытый SSH-ключ (одна строка из blesk_deploy.pub)"
    [[ $(key_blob "$owner") != "$(key_blob "$deploy")" ]] || die "ключ владельца и ключ деплоя должны быть разными"

    ensure_tools
    authorize_owner "$owner" "$deploy"
    prepare_deploy_user "$deploy"
    install_wrapper
    install_sudoers
    print_host_key

    say "Готово"
    echo "Проверь с ноутбука, что вход по ключу работает: ssh -i ~/.ssh/blesk_owner root@<IP сервера>"
    echo "Вход по паролю выключит первый деплой из GitHub, когда убедится, что ключи на месте."
}

# Everything runs from main, so a partly downloaded script does nothing at all.
main "$@"
