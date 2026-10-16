#!/usr/bin/env bash
# Brings a blesk server back from its backup onto a clean machine: deploy/.env, the certificate,
# the attachments and the database. scripts/deploy.sh runs it when BLESK_RESTORE is set, before
# it starts the stack; by hand:
#
#   sudo BLESK_RESTORE=latest /opt/blesk/scripts/restore.sh       (or a snapshot id)
#
# It refuses to run over a server that already has data, unless BLESK_RESTORE_FORCE=1.
set -euo pipefail
# shellcheck source=scripts/backup-lib.sh
source "$(dirname "$(readlink -f "$0")")/backup-lib.sh"

snapshot="${1:-${BLESK_RESTORE:-latest}}"
load_backup_env

if [[ ${BLESK_RESTORE_FORCE:-} != 1 ]]; then
    [[ ! -f "$DEPLOY_DIR/.env" ]] || fail "this server already has deploy/.env; BLESK_RESTORE_FORCE=1 replaces it"
    [[ -z "$(volume_path postgres-data)" ]] || fail "this server already has a database; BLESK_RESTORE_FORCE=1 replaces it"
fi

target="$BACKUP_STATE/restore"
rm -rf "$target"
install -d -m 700 "$target"
trap 'rm -rf "$target"' EXIT

log "fetching snapshot $snapshot"
restic -v "$target:/restore" -- restore "$snapshot" --host blesk --tag blesk --target /restore --quiet
state="$target/blesk/state"
[[ -s "$state/blesk.pgdump" && -f "$state/env" ]] || fail "the snapshot has no database dump or settings"

compose down --volumes --remove-orphans >/dev/null 2>&1 || true

install -m 600 "$state/env" "$DEPLOY_DIR/.env"
# The IP belongs to the old machine; deploy.sh finds the new one and, without a domain, issues a
# certificate for it.
sed -i 's|^BLESK_PUBLIC_IP=.*|BLESK_PUBLIC_IP=|' "$DEPLOY_DIR/.env"
mkdir -p "$DEPLOY_DIR/certs"
find "$DEPLOY_DIR/certs" -type f ! -name .gitkeep -delete
if [[ -d "$state/certs" ]]; then cp -a "$state/certs/." "$DEPLOY_DIR/certs/"; fi

log "restoring attachments"
# Labelled as Compose would, so it adopts the volume without warnings.
docker volume create --label com.docker.compose.project="$COMPOSE_PROJECT" --label com.docker.compose.volume=attachments \
    "${COMPOSE_PROJECT}_attachments" >/dev/null
docker run --rm --quiet -v "${COMPOSE_PROJECT}_attachments:/data" -v "$target/blesk/attachments:/from:ro" alpine:3.22 \
    sh -c 'cp -a /from/. /data/'

log "restoring the database"
compose --progress quiet up -d --wait postgres
compose exec -T postgres pg_restore -U blesk -d blesk --clean --if-exists --no-owner --exit-on-error <"$state/blesk.pgdump"

log "restored $snapshot: $(compose exec -T postgres psql -U blesk -d blesk -tAc 'select count(*) from accounts') accounts"
