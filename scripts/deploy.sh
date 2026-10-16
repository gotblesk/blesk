#!/usr/bin/env bash
# Installs or updates the blesk server on a clean Ubuntu 22.04/24.04 VPS (x86_64). Safe to re-run:
# secrets, the certificate and the data volumes are kept, only the code and containers are refreshed.
#
#   curl -fsSL https://raw.githubusercontent.com/gotblesk/blesk/master/scripts/deploy.sh | sudo bash
#
# Optional environment: BLESK_BRANCH (default master), BLESK_DOMAIN, BLESK_PUBLIC_IP, BLESK_DIR, BLESK_REPO.
# Backups (see scripts/backup-lib.sh): BACKUP_S3_ENDPOINT, BACKUP_S3_BUCKET, BACKUP_S3_REGION,
# BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY, BACKUP_PASSWORD. BLESK_RESTORE=latest (or a snapshot
# id) moves a server here from its backup. BLESK_SSH_KEYS_ONLY=1 turns off SSH passwords.
# BLESK_BUILD_HERE=1 builds the server on this machine instead of taking the one GitHub built.
set -euo pipefail

REPO_URL="${BLESK_REPO:-https://github.com/gotblesk/blesk}"
BRANCH="${BLESK_BRANCH:-master}"
INSTALL_DIR="${BLESK_DIR:-/opt/blesk}"
DEPLOY_DIR="$INSTALL_DIR/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
CERT_DIR="$DEPLOY_DIR/certs"

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*" >&2; }
die() {
    printf '\033[31mОшибка: %s\033[0m\n' "$*" >&2
    exit 1
}

check_system() {
    [[ $EUID -eq 0 ]] || die "запустите от root: curl -fsSL ... | sudo bash"
    [[ "$(uname -m)" == "x86_64" ]] || die "нужен сервер x86_64 (amd64): библиотека шифрования Signal собрана только для него"
    command -v apt-get >/dev/null || die "поддерживается только Ubuntu 22.04 или 24.04"
}

install_packages() {
    say "Устанавливаю пакеты"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl git jq openssl qrencode fail2ban unattended-upgrades ufw >/dev/null
    systemctl enable --now fail2ban unattended-upgrades >/dev/null 2>&1 || true
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

# Works for a fresh directory, an existing checkout and a directory without git alike.
fetch_code() {
    say "Получаю код ($BRANCH)"
    mkdir -p "$INSTALL_DIR"
    git -C "$INSTALL_DIR" init -q
    if git -C "$INSTALL_DIR" remote get-url origin >/dev/null 2>&1; then
        git -C "$INSTALL_DIR" remote set-url origin "$REPO_URL"
    else
        git -C "$INSTALL_DIR" remote add origin "$REPO_URL"
    fi
    git -C "$INSTALL_DIR" fetch -q --depth 1 origin "$BRANCH"
    git -C "$INSTALL_DIR" checkout -q -f -B "$BRANCH" FETCH_HEAD
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

ask_domain() {
    local answer=""
    if [[ -r /dev/tty ]] && { : </dev/tty; } 2>/dev/null; then
        printf '\nЕсть домен для сервера (например chat.example.ru)?\nВведите его или просто нажмите Enter: ' >/dev/tty
        read -r answer </dev/tty || answer=""
    fi
    echo "$answer" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]'
}

configure() {
    if [[ ! -f "$ENV_FILE" ]]; then
        say "Создаю настройки и пароли"
        umask 077
        cat >"$ENV_FILE" <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 24)
BLESK_DOMAIN=${BLESK_DOMAIN-$(ask_domain)}
BLESK_PUBLIC_IP=
BLESK_CADDYFILE=Caddyfile.domain
EOF
        umask 022
    elif [[ -n "${BLESK_DOMAIN+set}" ]]; then
        set_env_value BLESK_DOMAIN "$BLESK_DOMAIN"
    fi

    DOMAIN=$(env_value BLESK_DOMAIN)
    if [[ -n "$DOMAIN" ]]; then
        set_env_value BLESK_CADDYFILE Caddyfile.domain
    else
        PUBLIC_IP="${BLESK_PUBLIC_IP:-$(env_value BLESK_PUBLIC_IP)}"
        [[ -n "$PUBLIC_IP" ]] || PUBLIC_IP=$(detect_public_ip)
        [[ -n "$PUBLIC_IP" ]] || die "не удалось определить IP сервера, задайте BLESK_PUBLIC_IP"
        set_env_value BLESK_PUBLIC_IP "$PUBLIC_IP"
        set_env_value BLESK_CADDYFILE Caddyfile.ip
        make_certificate "$PUBLIC_IP"
    fi
    configure_calls
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
    if [[ -f "$crt" && -f "$key" ]] && openssl x509 -in "$crt" -noout -ext subjectAltName 2>/dev/null | grep -q "IP Address:$ip\$"; then
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

# SSH by key only. Done only when asked and when a key is already there, so nobody is locked out:
# the GitHub workflow that asks for it has just logged in with one.
harden_ssh() {
    [[ ${BLESK_SSH_KEYS_ONLY:-} == 1 ]] || return 0
    if [[ ! -s /root/.ssh/authorized_keys ]]; then
        warn "Ключей SSH у root нет, вход по паролю оставлен"
        return 0
    fi
    say "Вход по SSH — только по ключу"
    install -d -m 755 /etc/ssh/sshd_config.d
    cat >/etc/ssh/sshd_config.d/10-blesk.conf <<EOF
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
    sshd -t && { systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || true; }
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
ExecStart=$INSTALL_DIR/scripts/backup.sh
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
    "$INSTALL_DIR/scripts/backup.sh" || warn "Резервная копия не получилась, журнал выше"
}

# The server's build needs Signal's Maven repository, which is blocked in some countries (Russia
# among them), so the host takes the build GitHub Actions attached to the release of this commit.
fetch_server() {
    if [[ ${BLESK_BUILD_HERE:-} == 1 ]]; then
        set_env_value BLESK_SERVER_DOCKERFILE server/Dockerfile
        return
    fi
    say "Скачиваю сервер, собранный на GitHub"
    local commit api url="" dist="$INSTALL_DIR/server/dist" attempt
    commit=$(git -C "$INSTALL_DIR" rev-parse HEAD)
    api="https://api.github.com/repos/${REPO_URL#https://github.com/}/releases?per_page=30"
    for attempt in $(seq 1 60); do
        url=$(curl -fsSL "$api" 2>/dev/null | jq -r --arg commit "$commit" \
            'first(.[] | select(.draft | not) | select(.target_commitish == $commit) | .assets[] | select(.name == "blesk-server.tar.gz") | .browser_download_url) // empty' ||
            true)
        [[ -z "$url" ]] || break
        if ((attempt == 1)); then echo "Эта версия ещё собирается на GitHub (обычно до 30 минут), жду…"; fi
        sleep 60
    done
    [[ -n "$url" ]] || die "на GitHub нет собранного сервера для ${commit::7}: проверьте Actions → Build"
    rm -rf "$dist"
    mkdir -p "$dist"
    curl -fsSL "$url" | tar -xz -C "$dist"
    [[ -x "$dist/blesk-server/bin/blesk-server" ]] || die "архив сервера неполный"
    set_env_value BLESK_SERVER_DOCKERFILE server/Dockerfile.dist
}

start_stack() {
    say "Собираю и запускаю (первый раз это занимает несколько минут)"
    docker compose --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" up -d --build --remove-orphans
    docker image prune -f >/dev/null 2>&1 || true
}

wait_until_ready() {
    say "Жду, пока сервер ответит"
    for _ in $(seq 1 90); do
        if docker compose --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" \
            exec -T caddy wget -qO- http://server:8080/v1/info 2>/dev/null | grep -q '"blesk"'; then
            return
        fi
        sleep 2
    done
    docker compose --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" logs --tail 50 server >&2 || true
    die "сервер не запустился, журнал выше"
}

print_connection() {
    local link
    if [[ -n "$DOMAIN" ]]; then
        link="blesk://server?url=https://$DOMAIN"
        if ! curl -fsS --max-time 20 "https://$DOMAIN/v1/info" >/dev/null 2>&1; then
            warn "HTTPS для $DOMAIN пока не отвечает. Проверьте, что домен указывает на этот сервер; сертификат появится сам."
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
}

main() {
    check_system
    install_packages
    ensure_swap
    fetch_code
    # The checkout may have brought a newer version of this script; continue with that one.
    if [[ "${BLESK_DEPLOY_REEXEC:-}" != 1 && -f "$INSTALL_DIR/scripts/deploy.sh" ]]; then
        export BLESK_DEPLOY_REEXEC=1
        exec bash "$INSTALL_DIR/scripts/deploy.sh"
    fi
    configure_backup
    restore_if_asked
    configure
    open_firewall
    harden_ssh
    fetch_server
    start_stack
    wait_until_ready
    print_connection
    first_backup
}

# Everything runs from main, so bash has read the whole file before the checkout can replace it.
main "$@"
