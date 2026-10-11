#!/bin/bash
# ============================================================
# blesk — PostgreSQL автобэкап
# Запускается cron ежедневно в 04:00
# Хранит последние 14 дней бэкапов
# ============================================================

BACKUP_DIR="/var/backups/blesk-pg"
DB_NAME="blesk"
DB_USER="blesk"
RETENTION_DAYS=14
TIMESTAMP=$(date +%Y-%m-%d_%H%M)
FILENAME="blesk_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

# pg_dump сжатый через gzip
pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges | gzip > "${BACKUP_DIR}/${FILENAME}"

if [ $? -eq 0 ]; then
  SIZE=$(du -h "${BACKUP_DIR}/${FILENAME}" | cut -f1)
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] OK: ${FILENAME} (${SIZE})"
else
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] FAIL: pg_dump error" >&2
  exit 1
fi

# Удаляем бэкапы старше RETENTION_DAYS дней
find "$BACKUP_DIR" -name "blesk_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleanup done. Backups older than ${RETENTION_DAYS}d removed."
