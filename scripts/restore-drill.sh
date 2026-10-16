#!/usr/bin/env bash
# Proves the backups can be restored: takes the newest snapshot onto a throwaway machine (the
# GitHub runner in .github/workflows/backup-check.yml), brings up the database and the server on it
# and checks that the server answers and the data is there. Touches nothing on the real server.
#
#   BLESK_DIR=. BACKUP_S3_ENDPOINT=... BACKUP_S3_BUCKET=... BACKUP_S3_ACCESS_KEY=... \
#   BACKUP_S3_SECRET_KEY=... BACKUP_PASSWORD=... scripts/restore-drill.sh
#
# BLESK_MAX_AGE_HOURS (default 36): an older newest snapshot fails the drill, since the nightly
# backup has stopped. BLESK_DRILL=age stops after that check. BLESK_DRILL_SERVER=0 skips building
# and starting the server.
set -euo pipefail
here="$(dirname "$(readlink -f "$0")")"
# shellcheck source=scripts/backup-lib.sh
source "$here/backup-lib.sh"

# Never on a live server: the drill replaces deploy/.env and the volumes, then removes them.
[[ -n "${BLESK_DIR:-}" && ! -f "$DEPLOY_DIR/.env" ]] || fail "run from a repository checkout with BLESK_DIR set, never on the server"
write_backup_env || fail "BACKUP_S3_ENDPOINT, BACKUP_S3_BUCKET, BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY and BACKUP_PASSWORD are needed"
load_backup_env
cleanup() {
    compose down --volumes >/dev/null 2>&1 || true
    rm -f "$DEPLOY_DIR/.env" "$BACKUP_ENV"
    find "$DEPLOY_DIR/certs" -type f ! -name .gitkeep -delete 2>/dev/null || true
}
trap cleanup EXIT

latest=$(restic -- snapshots --host blesk --tag blesk --latest 1 --json)
time=$(grep -o '"time":"[^"]*"' <<<"$latest" | tail -n 1 | cut -d'"' -f4)
id=$(grep -o '"short_id":"[0-9a-f]*"' <<<"$latest" | tail -n 1 | cut -d'"' -f4)
[[ -n "$id" ]] || fail "there are no backups yet"
age_hours=$((($(date -u +%s) - $(date -u -d "$time" +%s)) / 3600))
log "newest snapshot $id, $age_hours h old"
((age_hours <= ${BLESK_MAX_AGE_HOURS:-36})) || fail "the newest backup is $age_hours hours old: the nightly backup is not running"
if [[ ${BLESK_DRILL:-full} == age ]]; then
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then echo "Newest backup $id is $age_hours h old." >>"$GITHUB_STEP_SUMMARY"; fi
    exit 0
fi

BLESK_RESTORE_FORCE=1 BLESK_BACKUP_STATE="${BLESK_BACKUP_STATE:-$(mktemp -d)}" bash "$here/restore.sh" "$id"

sql() { compose exec -T postgres psql -U blesk -d blesk -tAc "$1"; }
accounts=$(sql 'select count(*) from accounts')
attachments=$(sql 'select count(*) from attachments')
objects=$(docker run --rm --quiet -v "${COMPOSE_PROJECT}_minio-data:/data:ro" alpine:3.22 \
    sh -c 'find /data/blesk-attachments -type f -name xl.meta 2>/dev/null | wc -l')

if [[ ${BLESK_DRILL_SERVER:-1} == 1 ]]; then
    log "starting the server on the restored data"
    compose up -d --build --wait minio
    compose up -d --build server
    answered=""
    for _ in $(seq 1 90); do
        if docker run --rm --quiet --network "${COMPOSE_PROJECT}_default" alpine:3.22 wget -qO- http://server:8080/v1/info 2>/dev/null | grep -q '"blesk"'; then
            answered=1
            break
        fi
        sleep 2
    done
    [[ -n "$answered" ]] || {
        compose logs --tail 80 server >&2
        fail "the server did not start on the restored data"
    }
fi

summary="Snapshot $id ($time, $age_hours h old) restored: $accounts accounts, $attachments attachments, $objects stored objects."
log "$summary"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then echo "$summary" >>"$GITHUB_STEP_SUMMARY"; fi
