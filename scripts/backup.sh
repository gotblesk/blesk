#!/usr/bin/env bash
# Nightly backup of the blesk server to S3 storage at another provider. Run by the
# blesk-backup.timer that scripts/deploy.sh installs; safe to run by hand at any time:
#
#   sudo /opt/blesk/scripts/backup.sh
#
# What goes in: a consistent PostgreSQL dump (taken while the server runs), the attachment files
# (attachments, already encrypted on the phones), deploy/.env (passwords) and deploy/certs (the
# certificate the apps pin). Kept: 7 daily, 4 weekly and 6 monthly snapshots. On Sundays a tenth of
# the stored data is read back and checked. The journal records sizes and ids only.
set -euo pipefail
# shellcheck source=scripts/backup-lib.sh
source "$(dirname "$(readlink -f "$0")")/backup-lib.sh"

[[ $EUID -eq 0 ]] || fail "run as root"
load_backup_env

stage="$BACKUP_STATE/stage"
rm -rf "$stage"
install -d -m 700 "$stage"
trap 'rm -rf "$stage"' EXIT

log "dumping the database"
compose exec -T postgres pg_dump -U blesk -d blesk --format=custom >"$stage/blesk.pgdump"
[[ -s "$stage/blesk.pgdump" ]] || fail "empty database dump"
install -m 600 "$DEPLOY_DIR/.env" "$stage/env"
[[ -d "$DEPLOY_DIR/certs" ]] && cp -a "$DEPLOY_DIR/certs" "$stage/certs"

attachments=$(volume_path attachments)
[[ -n "$attachments" && -d "$attachments" ]] || fail "no attachments volume"

if ! restic -- cat config >/dev/null 2>&1; then
    log "creating the backup repository"
    restic -- init >/dev/null
fi

log "uploading"
# A fixed host name, so a new server finds the snapshots of the old one.
restic -v "$stage:/blesk/state:ro" -v "$attachments:/blesk/attachments:ro" -- \
    backup /blesk --host blesk --tag blesk --quiet --json | tail -n 1 >"$BACKUP_STATE/last.json"

restic -- forget --host blesk --tag blesk --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune --quiet

if [[ $(date +%u) == 7 || ${BLESK_BACKUP_CHECK:-} == 1 ]]; then
    log "reading back a tenth of the stored data"
    restic -- check --read-data-subset=10% --quiet
fi

date -u +%s >"$BACKUP_STATE/last-success"
log "done: $(grep -o '"snapshot_id":"[0-9a-f]*"' "$BACKUP_STATE/last.json" || true)"
