#!/usr/bin/env bash
# The server stack of the VPS, on this machine, for the integration and smoke tests: the same
# deploy/docker-compose.yml and server/Dockerfile.dist with the server Gradle just built, in the mode
# without a domain (Caddyfile.ip, a self-signed certificate the clients pin). It listens on
# https://127.0.0.1; an emulator reaches it through `adb reverse`.
#
#   scripts/ci/test-server.sh up        build and start, then print BLESK_IT_URL and BLESK_IT_PIN
#   scripts/ci/test-server.sh invites   print unused bootstrap invite codes, one per line
#   scripts/ci/test-server.sh logs      the server's log
#   scripts/ci/test-server.sh down      stop and delete everything, data included
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
deploy="$root/deploy"
ip=127.0.0.1
url="https://$ip"
env_file="$deploy/.env"
crt="$deploy/certs/blesk.crt"
key="$deploy/certs/blesk.key"

stack() { docker compose --project-directory "$deploy" -f "$deploy/docker-compose.yml" "$@"; }

# The same certificate scripts/deploy.sh makes for a bare IP (make_certificate).
make_certificate() {
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
        -keyout "$key" -out "$crt" -days 30 -subj "/CN=$ip" \
        -addext "subjectAltName=IP:$ip" \
        -addext "basicConstraints=critical,CA:FALSE" \
        -addext "keyUsage=critical,digitalSignature" \
        -addext "extendedKeyUsage=serverAuth" 2>/dev/null
    chmod 600 "$key"
}

pin() { openssl x509 -in "$crt" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64; }

up() {
    [[ -x "$root/server/build/install/blesk-server/bin/blesk-server" ]] ||
        { echo "no server build: run ./gradlew :server:installDist first" >&2; exit 1; }
    rm -rf "$root/server/dist"
    mkdir -p "$root/server/dist"
    cp -R "$root/server/build/install/blesk-server" "$root/server/dist/"
    [[ -f "$crt" && -f "$key" ]] || make_certificate
    # Made once like on the VPS: the database volume keeps the first password until `down`. Logs stay
# with Docker: a CI runner or a container may have no system journal.
    [[ -f "$env_file" ]] || cat >"$env_file" <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 24)
BLESK_DOMAIN=
BLESK_SITE_DOMAIN=
BLESK_PUBLIC_IP=$ip
BLESK_CADDYFILE=Caddyfile.ip
BLESK_SERVER_DOCKERFILE=server/Dockerfile.dist
BLESK_LOG_DRIVER=local
LIVEKIT_API_KEY=API$(openssl rand -hex 8)
LIVEKIT_API_SECRET=$(openssl rand -hex 32)
LIVEKIT_PUBLIC_URL=wss://$ip/livekit
EOF
    mkdir -p "$deploy/site-dist"
    stack up -d --build --quiet-pull
    for _ in $(seq 1 90); do
        if [[ $(curl -sS --max-time 5 --cacert "$crt" "$url/health" 2>/dev/null | jq -r .status 2>/dev/null) == ok ]]; then
            echo "BLESK_IT_URL=$url"
            echo "BLESK_IT_PIN=sha256/$(pin)"
            return
        fi
        sleep 2
    done
    stack ps >&2
    stack logs --tail 80 server caddy >&2
    echo "the test server did not become healthy" >&2
    exit 1
}

# A server that has seen many test runs may have used up its bootstrap invites; it gets more then.
invites() {
    local codes
    codes=$(stack exec -T server /opt/blesk-server/bin/blesk-server invites)
    [[ -n $codes ]] || codes=$(stack exec -T server /opt/blesk-server/bin/blesk-server invites add 10)
    sed -n 's#^https://blesk.su/i/##p' <<<"$codes"
}

case ${1:-} in
    up) up ;;
    invites) invites ;;
    logs) stack logs --no-color server ;;
    down)
        stack down -v --remove-orphans
        rm -rf "$env_file" "$crt" "$key" "$root/server/dist" "$deploy/site-dist"
        ;;
    *) sed -n '2,10p' "$0" >&2; exit 2 ;;
esac
