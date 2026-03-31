#!/bin/bash
# ============================================================
# blesk — Установка автобэкапов PostgreSQL на VPS
# Запускать один раз: sudo bash setup-backups.sh
# ============================================================

BACKUP_DIR="/var/backups/blesk-pg"
SCRIPT_PATH="/var/www/blesk/server/scripts/pg-backup.sh"
LOG_PATH="/var/log/blesk-backup.log"
CRON_USER="blesk"

echo "=== Настройка автобэкапов PostgreSQL для blesk ==="

# 1. Создаём директорию для бэкапов
mkdir -p "$BACKUP_DIR"
chown "$CRON_USER:$CRON_USER" "$BACKUP_DIR"
echo "[OK] Директория: $BACKUP_DIR"

# 2. Делаём скрипт исполняемым
chmod +x "$SCRIPT_PATH"
echo "[OK] Скрипт: $SCRIPT_PATH"

# 3. Создаём лог-файл
touch "$LOG_PATH"
chown "$CRON_USER:$CRON_USER" "$LOG_PATH"
echo "[OK] Лог: $LOG_PATH"

# 4. Добавляем cron задачу (ежедневно в 04:00)
CRON_LINE="0 4 * * * ${SCRIPT_PATH} >> ${LOG_PATH} 2>&1"
(crontab -u "$CRON_USER" -l 2>/dev/null | grep -v "pg-backup.sh"; echo "$CRON_LINE") | crontab -u "$CRON_USER" -
echo "[OK] Cron установлен для пользователя $CRON_USER:"
echo "     $CRON_LINE"

# 5. Тестовый запуск
echo ""
echo "=== Тестовый бэкап ==="
sudo -u "$CRON_USER" bash "$SCRIPT_PATH"

echo ""
echo "=== Готово! ==="
echo "Бэкапы: $BACKUP_DIR (хранятся 14 дней)"
echo "Лог:    $LOG_PATH"
echo "Cron:   ежедневно в 04:00"
