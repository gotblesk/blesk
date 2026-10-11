#!/bin/bash
# blesk database backup script
# Add to crontab: 0 3 * * * /home/blesk/blesk/server/scripts/backup.sh
# chmod +x /home/blesk/blesk/server/scripts/backup.sh

BACKUP_DIR="/home/blesk/backups"
DB_NAME="blesk"
RETENTION_DAYS=7
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p "$BACKUP_DIR"

# Create backup
pg_dump "$DB_NAME" | gzip > "$BACKUP_DIR/blesk_${DATE}.sql.gz"

# Check if backup was successful
if [ $? -eq 0 ]; then
    echo "[$(date)] Backup successful: blesk_${DATE}.sql.gz"
else
    echo "[$(date)] ERROR: Backup failed!" >&2
    exit 1
fi

# Remove backups older than retention period
find "$BACKUP_DIR" -name "blesk_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

echo "[$(date)] Cleanup complete. Retained last ${RETENTION_DAYS} days."
