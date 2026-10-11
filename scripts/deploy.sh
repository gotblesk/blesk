#!/usr/bin/env bash
# Installs or updates the blesk server on a clean Ubuntu 22.04/24.04 VPS (x86_64). Safe to re-run:
# secrets, the certificate and the data volumes are kept, only the code and containers are refreshed.
#
#   curl -fsSL https://raw.githubusercontent.com/gotblesk/blesk/master/scripts/deploy.sh | sudo bash
#
# Optional environment: BLESK_BRANCH (default master), BLESK_DOMAIN, BLESK_PUBLIC_IP, BLESK_DIR, BLESK_REPO.
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
    apt-get install -y -qq ca-certificates curl git openssl qrencode >/dev/null
    if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
        say "Устанавливаю Docker"
        curl -fsSL https://get.docker.com | sh
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
MINIO_ROOT_USER=blesk
MINIO_ROOT_PASSWORD=$(openssl rand -hex 24)
MINIO_IMAGE=cgr.dev/chainguard/minio:latest
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

open_firewall() {
    if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
        ufw allow 80/tcp >/dev/null
        ufw allow 443/tcp >/dev/null
        ufw allow 443/udp >/dev/null
    fi
}

start_stack() {
    say "Собираю и запускаю (первый раз это занимает несколько минут)"
    docker compose --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" up -d --build --remove-orphans
    docker image prune -f >/dev/null 2>&1 || true
}

wait_until_ready() {
    say "Жду, пока сервер ответит"
    local attempt
    for attempt in $(seq 1 90); do
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
    configure
    open_firewall
    start_stack
    wait_until_ready
    print_connection
}

# Everything runs from main, so bash has read the whole file before the checkout can replace it.
main "$@"
