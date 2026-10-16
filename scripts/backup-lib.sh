# shellcheck shell=bash disable=SC2034
# Shared by scripts/backup.sh, scripts/restore.sh and the restore drill; sourced, not run.
#
# Backups are a restic repository in S3-compatible storage at a provider other than the server's.
# restic encrypts everything on the server before it leaves (AES-256, key derived from
# BACKUP_PASSWORD), so the storage provider sees only opaque blobs. Without the password the
# backups cannot be read by anyone, including us: the owner keeps it in GitHub secrets and offline.
#
# deploy/backup.env holds:
#   BACKUP_S3_ENDPOINT    e.g. https://s3.ru-1.storage.selcloud.ru
#   BACKUP_S3_BUCKET      e.g. blesk-backup
#   BACKUP_S3_REGION      optional, e.g. ru-1
#   BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY
#   BACKUP_PASSWORD

INSTALL_DIR="${BLESK_DIR:-/opt/blesk}"
DEPLOY_DIR="$INSTALL_DIR/deploy"
BACKUP_ENV="$DEPLOY_DIR/backup.env"
BACKUP_STATE="${BLESK_BACKUP_STATE:-/var/lib/blesk-backup}"
COMPOSE_PROJECT=blesk
RESTIC_IMAGE=restic/restic:0.17.3
BACKUP_KEYS=(BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY BACKUP_PASSWORD)

log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*"; }
fail() {
    log "error: $*" >&2
    exit 1
}

compose() { docker compose --project-name "$COMPOSE_PROJECT" --project-directory "$DEPLOY_DIR" -f "$DEPLOY_DIR/docker-compose.yml" "$@"; }

load_backup_env() {
    [[ -f "$BACKUP_ENV" ]] || fail "no $BACKUP_ENV: backups are not set up"
    set -a
    # shellcheck disable=SC1090
    source "$BACKUP_ENV"
    set +a
    local key
    for key in "${BACKUP_KEYS[@]}"; do
        [[ -n "${!key:-}" ]] || fail "$key is empty in $BACKUP_ENV"
    done
}

# Writes deploy/backup.env from variables of the same names, when all of them are given.
write_backup_env() {
    local key
    for key in "${BACKUP_KEYS[@]}"; do
        [[ -n "${!key:-}" ]] || return 1
    done
    (
        umask 077
        {
            for key in "${BACKUP_KEYS[@]}" BACKUP_S3_REGION; do
                printf '%s=%q\n' "$key" "${!key:-}"
            done
        } >"$BACKUP_ENV"
    )
}

# restic in a container, so the host needs nothing but Docker. Extra docker arguments (mounts)
# come before "--", restic arguments after it.
restic() {
    local docker_args=()
    while [[ $# -gt 0 && $1 != -- ]]; do
        docker_args+=("$1")
        shift
    done
    [[ ${1:-} == -- ]] && shift
    docker run --rm -i --quiet \
        -e RESTIC_REPOSITORY="s3:${BACKUP_S3_ENDPOINT%/}/$BACKUP_S3_BUCKET/blesk" \
        -e RESTIC_PASSWORD="$BACKUP_PASSWORD" \
        -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" \
        -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
        -e AWS_DEFAULT_REGION="${BACKUP_S3_REGION:-us-east-1}" \
        -v blesk-restic-cache:/root/.cache/restic \
        "${docker_args[@]}" \
        "$RESTIC_IMAGE" "$@"
}

volume_path() { docker volume inspect "${COMPOSE_PROJECT}_$1" --format '{{.Mountpoint}}' 2>/dev/null; }
