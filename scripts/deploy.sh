#!/usr/bin/env bash
# Installs or updates the blesk server on a clean Ubuntu 22.04/24.04 VPS (x86_64). Safe to re-run:
# secrets, the certificate and the data volumes are kept, only the code and containers are refreshed.
#
#   curl -fsSL https://raw.githubusercontent.com/gotblesk/blesk/master/scripts/deploy.sh | sudo bash
#
# Optional environment: BLESK_BRANCH (default master), BLESK_COMMIT (a full SHA to deploy instead of
# the newest commit of the branch), BLESK_DOMAIN (the API, e.g. api.blesk.su), BLESK_SITE_DOMAIN (the
# site, e.g. blesk.su; works only together with BLESK_DOMAIN), BLESK_PUBLIC_IP, BLESK_DIR, BLESK_REPO.
# Backups (see scripts/backup-lib.sh): BACKUP_S3_ENDPOINT, BACKUP_S3_BUCKET, BACKUP_S3_REGION,
# BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY, BACKUP_PASSWORD. BLESK_RESTORE=latest (or a snapshot
# id) moves a server here from its backup. BLESK_SSH_KEYS_ONLY=1 turns off SSH passwords.
# BLESK_BUILD_HERE=1 builds the server on this machine instead of taking the one GitHub built.
set -euo pipefail

REPO_URL="${BLESK_REPO:-https://github.com/gotblesk/blesk}"
BRANCH="${BLESK_BRANCH:-master}"
COMMIT="${BLESK_COMMIT:-}"
INSTALL_DIR="${BLESK_DIR:-/opt/blesk}"
DEPLOY_DIR="$INSTALL_DIR/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
CERT_DIR="$DEPLOY_DIR/certs"
SITE_DIR="$DEPLOY_DIR/site-dist"
# The GitHub release of the checked-out commit as JSON, once find_release has found it.
RELEASE=""

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*" >&2; }
die() {
    printf '\033[31mОшибка: %s\033[0m\n' "$*" >&2
    exit 1
}

stack() { docker compose --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" "$@"; }

check_system() {
    [[ $EUID -eq 0 ]] || die "запустите от root: curl -fsSL ... | sudo bash"
    [[ "$(uname -m)" == "x86_64" ]] || die "нужен сервер x86_64 (amd64): библиотека шифрования Signal собрана только для него"
    command -v apt-get >/dev/null || die "поддерживается только Ubuntu 22.04 или 24.04"
}

install_packages() {
    say "Устанавливаю пакеты"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl git jq openssl python3 python3-systemd qrencode fail2ban unattended-upgrades ufw >/dev/null
    if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
        say "Устанавливаю Docker"
        curl -fsSL https://get.docker.com | sh
    fi
    # Docker Hub refuses some regions from time to time; Google's mirror of it is tried first and
    # Docker falls back to the Hub itself when the mirror lacks an image.
    if [[ ! -f /etc/docker/daemon.json ]]; then
        install -d /etc/docker
        printf '{\n  "registry-mirrors": ["https://mirror.gcr.io"]\n}\n' >/etc/docker/daemon.json
        systemctl restart docker >/dev/null 2>&1 || true
    fi
    systemctl enable --now docker >/dev/null 2>&1 || true
}

# fail2ban for SSH, security updates every night, logs for seven days: each is checked to really be
# running, since a server that only looks protected is worse than one known to be bare.
harden_host() {
    say "Защита сервера: fail2ban, обновления безопасности, журналы"
    local port tries
    port=$(ssh_port)
    install -d /etc/fail2ban/jail.d
    cat >/etc/fail2ban/jail.d/blesk.conf <<EOF
[sshd]
enabled = true
backend = systemd
port = ${port:-22}
maxretry = 5
findtime = 10m
bantime = 1h
EOF
    systemctl unmask fail2ban.service >/dev/null 2>&1 || die "не удалось снять запрет с fail2ban"
    systemctl enable fail2ban >/dev/null 2>&1 || die "fail2ban не включается: systemctl status fail2ban"
    systemctl restart fail2ban || die "fail2ban не запустился: journalctl -u fail2ban"
    for tries in $(seq 1 15); do
        fail2ban-client status sshd >/dev/null 2>&1 && break
        ((tries < 15)) || die "fail2ban запущен, но не следит за SSH: fail2ban-client status sshd"
        sleep 1
    done

    cat >/etc/apt/apt.conf.d/20auto-upgrades <<EOF
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
    # Hosting images mask apt's services to keep apt free on first boot. Their timers then report a
    # successful start and stop at once, so the services are unmasked along with the timers.
    systemctl unmask apt-daily.service apt-daily-upgrade.service apt-daily.timer apt-daily-upgrade.timer \
        unattended-upgrades.service >/dev/null 2>&1 || die "не удалось снять запрет с обновлений безопасности"
    systemctl enable --now unattended-upgrades apt-daily.timer apt-daily-upgrade.timer >/dev/null 2>&1 ||
        die "не удалось включить обновления безопасности: systemctl status unattended-upgrades"
    [[ $(apt-config dump APT::Periodic::Unattended-Upgrade) == *'"1"'* ]] ||
        die "обновления безопасности выключены в настройках apt"
    local timer
    for timer in apt-daily.timer apt-daily-upgrade.timer; do
        if ! systemctl is-active --quiet "$timer"; then
            # Started by the enable above when it could be; one more try now that nothing is masked.
            systemctl restart "$timer" >/dev/null 2>&1 || true
        fi
        if ! systemctl is-active --quiet "$timer"; then
            systemctl status "$timer" "${timer%.timer}.service" --no-pager >&2 || true
            die "таймер обновлений безопасности $timer не работает: состояние выше"
        fi
    done

    install -d /etc/systemd/journald.conf.d
    cat >/etc/systemd/journald.conf.d/blesk.conf <<EOF
[Journal]
MaxRetentionSec=7day
SystemMaxUse=1G
EOF
    systemctl restart systemd-journald
}

# Docker marks a hung server unhealthy but does not restart it; this timer does, every minute. The
# connection limit has to be set again whenever Docker rebuilds its rules, so it follows docker.service.
install_guards() {
    cat >/etc/systemd/system/blesk-watchdog.service <<EOF
[Unit]
Description=Restart blesk containers that fail their health check
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'docker ps -q --filter label=com.docker.compose.project=blesk --filter health=unhealthy | xargs -r docker restart'
EOF
    cat >/etc/systemd/system/blesk-watchdog.timer <<EOF
[Unit]
Description=Look for unhealthy blesk containers every minute

[Timer]
OnBootSec=2min
OnUnitActiveSec=1min

[Install]
WantedBy=timers.target
EOF
    cat >/etc/systemd/system/blesk-connlimit.service <<EOF
[Unit]
Description=Limit connections to blesk per address
After=docker.service
BindsTo=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/bash $INSTALL_DIR/scripts/connlimit.sh

[Install]
WantedBy=docker.service
EOF
    systemctl daemon-reload
    systemctl enable --now blesk-watchdog.timer >/dev/null
    systemctl enable blesk-connlimit.service >/dev/null
    systemctl restart blesk-connlimit.service || die "не удалось ограничить соединения: journalctl -u blesk-connlimit"
}

# Building the server needs more memory than a 2 GB machine has free next to the running stack.
ensure_swap() {
    local mem_kb swap_kb
    mem_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
    swap_kb=$(awk '/SwapTotal/ {print $2}' /proc/meminfo)
    if ((mem_kb < 3500000 && swap_kb == 0)) && [[ ! -f /swapfile ]]; then
        say "Добавляю 2 ГБ подкачки"
        fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
        chmod 600 /swapfile
        mkswap /swapfile >/dev/null
        swapon /swapfile
        grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
    fi
}

# Works for a fresh directory, an existing checkout and a directory without git alike. A pinned
# commit makes a deploy from CI install exactly what was built and checked, even when the branch has
# moved on since. It is looked for only among the recent commits of the branch and never fetched by
# its SHA: GitHub keeps the commits of every fork of the repository in one store, so a SHA alone may
# name anybody's code. A pinned commit not found there gives way to the newest one of the branch.
fetch_code() {
    local depth=1 target
    say "Получаю код ($BRANCH${COMMIT:+, ${COMMIT::7}})"
    [[ "$BRANCH" =~ ^[A-Za-z0-9._/-]+$ && "$BRANCH" != *..* && "$BRANCH" != [/-]* ]] ||
        die "BLESK_BRANCH=$BRANCH — это не имя ветки"
    [[ -z "$COMMIT" || "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "BLESK_COMMIT должен быть полным SHA коммита (40 знаков)"
    [[ -z "$COMMIT" ]] || depth=50
    mkdir -p "$INSTALL_DIR"
    git -C "$INSTALL_DIR" init -q
    if git -C "$INSTALL_DIR" remote get-url origin >/dev/null 2>&1; then
        git -C "$INSTALL_DIR" remote set-url origin "$REPO_URL"
    else
        git -C "$INSTALL_DIR" remote add origin "$REPO_URL"
    fi
    git -C "$INSTALL_DIR" fetch -q --depth "$depth" origin "refs/heads/$BRANCH"
    target=$(git -C "$INSTALL_DIR" rev-parse FETCH_HEAD)
    if [[ -n "$COMMIT" && "$COMMIT" != "$target" ]]; then
        if git -C "$INSTALL_DIR" merge-base --is-ancestor "$COMMIT" "$target" 2>/dev/null; then
            target=$COMMIT
        else
            warn "Коммита ${COMMIT::7} нет среди последних $depth коммитов $BRANCH, беру последний"
        fi
    fi
    git -C "$INSTALL_DIR" checkout -q -f -B "$BRANCH" "$target"
}

env_value() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2-; }

set_env_value() {
    if grep -qE "^$1=" "$ENV_FILE"; then
        sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
    else
        echo "$1=$2" >>"$ENV_FILE"
    fi
}

detect_public_ip() {
    local ip
    for url in https://api.ipify.org https://ifconfig.me/ip https://icanhazip.com; do
        ip=$(curl -4 -fsS --max-time 5 "$url" 2>/dev/null | tr -d '[:space:]') || true
        [[ $ip =~ ^[0-9]+(\.[0-9]+){3}$ ]] && { echo "$ip"; return; }
    done
    hostname -I | awk '{print $1}'
}

# Lowercase host names only: the value goes into .env through sed and into the Caddyfile as is.
check_domain() {
    [[ -z "$2" || "$2" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]$ ]] ||
        die "$1=$2 — это не доменное имя (нужно, например, chat.example.ru, строчными буквами)"
}

ask_domain() {
    local answer=""
    if [[ -r /dev/tty ]] && { : </dev/tty; } 2>/dev/null; then
        printf '\nЕсть домен для сервера (например chat.example.ru)?\nВведите его или просто нажмите Enter: ' >/dev/tty
        read -r answer </dev/tty || answer=""
    fi
    echo "$answer" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]'
}

configure() {
    check_domain BLESK_DOMAIN "${BLESK_DOMAIN:-}"
    check_domain BLESK_SITE_DOMAIN "${BLESK_SITE_DOMAIN:-}"
    if [[ ! -f "$ENV_FILE" ]]; then
        local domain
        domain=${BLESK_DOMAIN-$(ask_domain)}
        check_domain BLESK_DOMAIN "$domain"
        say "Создаю настройки и пароли"
        umask 077
        cat >"$ENV_FILE" <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 24)
BLESK_DOMAIN=$domain
BLESK_SITE_DOMAIN=${BLESK_SITE_DOMAIN:-}
BLESK_PUBLIC_IP=
BLESK_CADDYFILE=Caddyfile.domain
EOF
        umask 022
    else
        [[ -z "${BLESK_DOMAIN+set}" ]] || set_env_value BLESK_DOMAIN "$BLESK_DOMAIN"
        [[ -z "${BLESK_SITE_DOMAIN+set}" ]] || set_env_value BLESK_SITE_DOMAIN "$BLESK_SITE_DOMAIN"
    fi

    DOMAIN=$(env_value BLESK_DOMAIN)
    SITE_DOMAIN=$(env_value BLESK_SITE_DOMAIN)
    if [[ -n "$DOMAIN" ]]; then
        configure_site
    else
        if [[ -n "$SITE_DOMAIN" ]]; then
            warn "Сайт $SITE_DOMAIN не включён: он работает только вместе с доменом для API (BLESK_DOMAIN)"
            SITE_DOMAIN=""
        fi
        PUBLIC_IP="${BLESK_PUBLIC_IP:-$(env_value BLESK_PUBLIC_IP)}"
        [[ -n "$PUBLIC_IP" ]] || PUBLIC_IP=$(detect_public_ip)
        [[ -n "$PUBLIC_IP" ]] || die "не удалось определить IP сервера, задайте BLESK_PUBLIC_IP"
        set_env_value BLESK_PUBLIC_IP "$PUBLIC_IP"
        set_env_value BLESK_CADDYFILE Caddyfile.ip
        make_certificate "$PUBLIC_IP"
    fi
    configure_calls
}

# With a site domain one Caddyfile serves the API, the site and its www alias.
configure_site() {
    if [[ -z "$SITE_DOMAIN" ]]; then
        set_env_value BLESK_CADDYFILE Caddyfile.domain
        return
    fi
    [[ "$SITE_DOMAIN" != "$DOMAIN" ]] || die "сайту и API нужны разные домены (например blesk.su и api.blesk.su)"
    set_env_value BLESK_CADDYFILE Caddyfile.site
}

# LiveKit keys are made once and kept, like the other passwords; the address follows the host.
configure_calls() {
    [[ -n "$(env_value LIVEKIT_API_KEY)" ]] || set_env_value LIVEKIT_API_KEY "API$(openssl rand -hex 8)"
    [[ -n "$(env_value LIVEKIT_API_SECRET)" ]] || set_env_value LIVEKIT_API_SECRET "$(openssl rand -hex 32)"
    set_env_value LIVEKIT_PUBLIC_URL "wss://${DOMAIN:-$PUBLIC_IP}/livekit"
}

# Self-signed ECDSA P-256 certificate for the bare IP. Kept across runs, so the pin in the
# app stays valid; made again only when the IP changes.
make_certificate() {
    local ip=$1 crt="$CERT_DIR/blesk.crt" key="$CERT_DIR/blesk.key"
    mkdir -p "$CERT_DIR"
    if [[ -f "$crt" && -f "$key" && $'\n'$(openssl x509 -in "$crt" -noout -ext subjectAltName 2>/dev/null)$'\n' == *"IP Address:$ip"$'\n'* ]]; then
        return
    fi
    say "Создаю сертификат для $ip"
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
        -keyout "$key" -out "$crt" -days 3650 -subj "/CN=$ip" \
        -addext "subjectAltName=IP:$ip" \
        -addext "basicConstraints=critical,CA:FALSE" \
        -addext "keyUsage=critical,digitalSignature" \
        -addext "extendedKeyUsage=serverAuth" 2>/dev/null
    chmod 600 "$key"
}

ssh_port() { sshd -T 2>/dev/null | awk '/^port / {print $2; exit}' || true; }

# Only what blesk serves, plus SSH. The SSH port is opened before the firewall goes up, so the
# session that runs this is never cut off.
open_firewall() {
    if command -v ufw >/dev/null; then
        local port
        port=$(ssh_port)
        ufw allow "${port:-22}/tcp" >/dev/null
        ufw allow 80/tcp >/dev/null
        ufw allow 443/tcp >/dev/null
        ufw allow 443/udp >/dev/null
        # Call media: TCP fallback and the UDP range from deploy/livekit.yaml.
        ufw allow 7881/tcp >/dev/null
        ufw allow 50000:50100/udp >/dev/null
        ufw --force enable >/dev/null
    fi
}

has_ssh_keys() {
    local home
    home=$(getent passwd "$1" | cut -d: -f6)
    [[ -n "$home" && -f "$home/.ssh/authorized_keys" ]] && grep -qvE '^[[:space:]]*(#|$)' "$home/.ssh/authorized_keys"
}

# SSH by key only. Done only when asked and when every account that logs in has a key, so nobody is
# locked out: root for the owner and, once scripts/server-bootstrap.sh has made it, deploy for CI.
harden_ssh() {
    [[ ${BLESK_SSH_KEYS_ONLY:-} == 1 ]] || return 0
    # Until scripts/server-bootstrap.sh has run (it creates deploy), the owner's own key may not be on
    # the server yet, and the bootstrap itself is started with the root password.
    if ! id deploy >/dev/null 2>&1; then
        warn "Сервер ещё не подготовлен скриптом server-setup, вход по паролю оставлен"
        return 0
    fi
    local user
    for user in root deploy; do
        if ! has_ssh_keys "$user"; then
            warn "Ключей SSH у $user нет, вход по паролю оставлен"
            return 0
        fi
    done
    say "Вход по SSH — только по ключу"
    install -d -m 755 /etc/ssh/sshd_config.d
    # sshd keeps the first value it reads. Hosting images turn passwords on in sshd_config itself or
    # in drop-ins named to sort before ours (50-cloud-init.conf, 00-<hoster>.conf), and some leave out
    # the Include line, so every yes is turned off where it stands and the drop-ins are read first.
    cat >/etc/ssh/sshd_config.d/10-blesk.conf <<EOF
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
    grep -qiE '^[[:space:]]*Include[[:space:]]+/etc/ssh/sshd_config\.d/\*\.conf' /etc/ssh/sshd_config ||
        sed -i '1i Include /etc/ssh/sshd_config.d/*.conf' /etc/ssh/sshd_config
    local file
    for file in /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf; do
        [[ -f $file ]] || continue
        sed -i -E 's/^([[:space:]]*)(PasswordAuthentication|KbdInteractiveAuthentication|ChallengeResponseAuthentication)([[:space:]]+)yes([[:space:]]*)$/\1\2\3no\4/I' "$file"
    done
    if ! sshd -t; then
        rm -f /etc/ssh/sshd_config.d/10-blesk.conf
        warn "sshd отверг настройки, вход по паролю оставлен"
        return 0
    fi
    systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || true
    # Read whole, not piped into grep -q: grep stops at the match, sshd dies writing the rest, and
    # pipefail would count that as a failed check.
    local effective
    effective=$(sshd -T 2>/dev/null || true)
    if ! grep -qx 'passwordauthentication no' <<<"$effective"; then
        grep -rniE '^[[:space:]]*(Include|PasswordAuthentication|KbdInteractiveAuthentication)' \
            /etc/ssh/sshd_config /etc/ssh/sshd_config.d >&2 || true
        warn "sshd всё ещё разрешает вход по паролю: строки выше показывают, где"
    fi
}

# Writes deploy/backup.env when the backup settings are passed in, and keeps the nightly timer in
# place whenever that file exists.
configure_backup() {
    # shellcheck source=scripts/backup-lib.sh
    source "$INSTALL_DIR/scripts/backup-lib.sh"
    if write_backup_env; then say "Настройки резервных копий сохранены"; fi
    [[ -f "$BACKUP_ENV" ]] || return 0
    cat >/etc/systemd/system/blesk-backup.service <<EOF
[Unit]
Description=blesk backup to S3
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/bin/bash $INSTALL_DIR/scripts/backup.sh
Nice=10
IOSchedulingClass=idle
EOF
    cat >/etc/systemd/system/blesk-backup.timer <<EOF
[Unit]
Description=Nightly blesk backup

[Timer]
OnCalendar=*-*-* 03:30:00 Europe/Moscow
RandomizedDelaySec=15m
Persistent=true

[Install]
WantedBy=timers.target
EOF
    systemctl daemon-reload
    systemctl enable --now blesk-backup.timer >/dev/null
}

# A move from another server: its settings, certificate, files and database come from the backup.
restore_if_asked() {
    [[ -n "${BLESK_RESTORE:-}" ]] || return 0
    [[ -f "$DEPLOY_DIR/backup.env" ]] || die "для переноса нужны настройки резервных копий (BACKUP_*)"
    say "Переношу данные из резервной копии ($BLESK_RESTORE)"
    bash "$INSTALL_DIR/scripts/restore.sh" "$BLESK_RESTORE"
}

# Right after a move or the first setup there is no snapshot from this server yet: make one now.
first_backup() {
    [[ -f "$DEPLOY_DIR/backup.env" ]] || return 0
    [[ -n "${BLESK_RESTORE:-}" || ! -f /var/lib/blesk-backup/last-success ]] || return 0
    say "Делаю первую резервную копию"
    bash "$INSTALL_DIR/scripts/backup.sh" || warn "Резервная копия не получилась, журнал выше"
}

# The release GitHub Actions published for the checked-out commit. A push deploys only after its
# build has finished; a manual run may come earlier and then waits for the build, up to $1 minutes.
find_release() {
    local minutes=$1 commit api attempt
    [[ -z "$RELEASE" ]] || return 0
    commit=$(git -C "$INSTALL_DIR" rev-parse HEAD)
    api="https://api.github.com/repos/${REPO_URL#https://github.com/}/releases?per_page=30"
    for attempt in $(seq 1 "$minutes"); do
        RELEASE=$(curl -fsSL "$api" 2>/dev/null | jq -c --arg commit "$commit" \
            'first(.[] | select(.draft | not) | select(.target_commitish == $commit)
                | select(any(.assets[]; .name == "blesk-server.tar.gz"))) // empty' || true)
        [[ -z "$RELEASE" ]] || return 0
        ((attempt < minutes)) || break
        if ((attempt == 1)); then echo "Эта версия ещё собирается на GitHub (обычно до 30 минут), жду…"; fi
        sleep 60
    done
    return 1
}

release_asset() { jq -r --arg name "$1" 'first(.assets[] | select(.name == $name) | .browser_download_url) // empty' <<<"$RELEASE"; }

# The server's build needs Signal's Maven repository, which is blocked in some countries (Russia
# among them), so the host takes the build GitHub Actions attached to the release of this commit.
fetch_server() {
    if [[ ${BLESK_BUILD_HERE:-} == 1 ]]; then
        set_env_value BLESK_SERVER_DOCKERFILE server/Dockerfile
        return
    fi
    say "Скачиваю сервер, собранный на GitHub"
    local dist="$INSTALL_DIR/server/dist"
    find_release 60 ||
        die "на GitHub нет собранного сервера для $(git -C "$INSTALL_DIR" rev-parse --short HEAD): проверьте Actions → Build"
    rm -rf "$dist"
    mkdir -p "$dist"
    curl -fsSL "$(release_asset blesk-server.tar.gz)" | tar -xz -C "$dist"
    [[ -x "$dist/blesk-server/bin/blesk-server" ]] || die "архив сервера неполный"
    set_env_value BLESK_SERVER_DOCKERFILE server/Dockerfile.dist
}

# The site is built from this checkout, with the APK and its checksums from the same release, into
# site-dist.next, which no container uses; publish_site puts it in place once the stack is up.
build_site() {
    local next="$SITE_DIR.next" work release_url="" apk_url="" release_args=()
    rm -rf "$next"
    [[ -n "$SITE_DOMAIN" ]] || return 0
    [[ -f "$INSTALL_DIR/scripts/build-site.py" ]] || die "в этой версии нет scripts/build-site.py, сайт не собрать"
    say "Собираю сайт $SITE_DOMAIN"
    if find_release 1; then
        release_url=$(release_asset release.json)
        apk_url=$(release_asset blesk.apk)
    fi
    [[ -n "$release_url" && -n "$apk_url" ]] || warn "В релизе этой версии нет APK и release.json: сайт будет без скачивания"
    work=$(mktemp -d)
    mkdir -p "$next"
    if [[ -n "$release_url" ]]; then
        curl -fsSL "$release_url" -o "$work/release.json"
        release_args=(--release "$work/release.json")
    fi
    python3 "$INSTALL_DIR/scripts/build-site.py" --out "$next" "${release_args[@]}" || die "сайт не собрался, журнал выше"
    if [[ -n "$release_url" && -n "$apk_url" ]]; then
        mkdir -p "$next/download"
        curl -fsSL "$apk_url" -o "$next/download/blesk.apk"
        [[ "$(sha256sum "$next/download/blesk.apk" | cut -d' ' -f1)" == "$(jq -r .apkSha256 "$work/release.json")" ]] ||
            die "APK скачался с ошибкой: SHA-256 не совпадает с release.json"
    fi
    chmod -R a+rX "$next"
    rm -rf "$work"
}

start_stack() {
    say "Собираю и запускаю (первый раз это занимает несколько минут)"
    mkdir -p "$SITE_DIR"
    stack up -d --build --remove-orphans
    publish_site
    recreate_mounted
    # Caddy has just been made anew on the current site-dist, so nothing serves the old trees now.
    rm -rf "$SITE_DIR".old*
    docker image prune -f >/dev/null 2>&1 || true
}

# A running Caddy goes on serving a tree moved aside, since its bind mount holds the directory and
# not the path, until recreate_mounted makes a new container. So the trees moved aside are named
# apart and removed only after that: a deploy that fails earlier leaves the site being served intact.
publish_site() {
    [[ -d "$SITE_DIR.next" ]] || return 0
    mv "$SITE_DIR" "$SITE_DIR.old.$(date +%s)"
    mv "$SITE_DIR.next" "$SITE_DIR"
}

# A bind mount holds what was at its path when the container was made, while git replaces changed
# files with new ones and publish_site swaps the whole site directory: without a new container Caddy
# and LiveKit would go on with the old ones. Caddy restarts in a moment, so it is recreated every
# time; LiveKit only when its config changed, since that ends the calls in progress.
recreate_mounted() {
    local services=(caddy) stamp="$DEPLOY_DIR/.livekit.sha256" sum
    sum=$(sha256sum "$DEPLOY_DIR/livekit.yaml" | cut -d' ' -f1)
    [[ "$(cat "$stamp" 2>/dev/null)" == "$sum" ]] || services+=(livekit)
    stack up -d --no-deps --force-recreate "${services[@]}"
    printf '%s\n' "$sum" >"$stamp"
}

# /health answers only once the server is up and its database replies.
wait_until_ready() {
    say "Жду, пока сервер ответит"
    for _ in $(seq 1 90); do
        if [[ $(stack exec -T caddy wget -qO- http://server:8080/health 2>/dev/null) == *'"ok"'* ]]; then
            return
        fi
        sleep 2
    done
    stack logs --tail 50 server >&2 || true
    die "сервер не запустился, журнал выше"
}

print_connection() {
    local link
    if [[ -n "$DOMAIN" ]]; then
        link="blesk://server?url=https://$DOMAIN"
        if ! curl -fsS --max-time 20 "https://$DOMAIN/health" >/dev/null 2>&1; then
            warn "HTTPS для $DOMAIN пока не отвечает. Проверьте, что домен указывает на этот сервер; сертификат появится сам."
        fi
        if [[ -n "$SITE_DOMAIN" ]] && ! curl -fsS --max-time 20 -o /dev/null "https://$SITE_DOMAIN/" 2>/dev/null; then
            warn "Сайт https://$SITE_DOMAIN пока не отвечает. Проверьте A-записи $SITE_DOMAIN и www.$SITE_DOMAIN."
        fi
    else
        local crt="$CERT_DIR/blesk.crt" pin fingerprint
        pin=$(openssl x509 -in "$crt" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64)
        fingerprint=$(openssl x509 -in "$crt" -outform DER | openssl dgst -sha256 | awk '{print $NF}')
        link="blesk://server?url=https://$PUBLIC_IP&pin=sha256/$pin"
        printf '\nОтпечаток сертификата (SHA-256):\n%s\n' "$fingerprint"
    fi
    say "Готово. В приложении: Настройки → Сервер → Сканировать QR"
    qrencode -t ANSIUTF8 "$link"
    printf '%s\n' "$link"
    if [[ -n "${SITE_DOMAIN:-}" ]]; then printf '\nСайт: https://%s\n' "$SITE_DOMAIN"; fi
}

main() {
    check_system
    install_packages
    ensure_swap
    fetch_code
    # The checkout may have brought another version of this script; continue with that one. The
    # guard holds the commit rather than a flag: the script from the newest commit of the branch may
    # itself check out an older pinned one, and then hands over to that commit's script in turn.
    local head
    head=$(git -C "$INSTALL_DIR" rev-parse HEAD)
    if [[ "${BLESK_DEPLOY_REEXEC:-}" != "$head" && -f "$INSTALL_DIR/scripts/deploy.sh" ]]; then
        export BLESK_DEPLOY_REEXEC=$head
        exec bash "$INSTALL_DIR/scripts/deploy.sh"
    fi
    configure_backup
    restore_if_asked
    configure
    open_firewall
    harden_ssh
    harden_host
    fetch_server
    build_site
    start_stack
    install_guards
    wait_until_ready
    print_connection
    first_backup
}

# Everything runs from main, so bash has read the whole file before the checkout can replace it.
main "$@"
