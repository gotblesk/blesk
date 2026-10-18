#!/usr/bin/env bash
# Saves blesk's log from the phone (or from the emulator «Б») into a file for a bug report: device
# and app version on top, then the app's own lines and its crashes.
#
#   bash scripts/owner/logs.sh [--emulator]
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

target=phone
while [[ $# -gt 0 ]]; do
    case $1 in
        --emulator | -e) target=emulator ;;
        *) fail "Неизвестный параметр: $1. Можно только --emulator." ;;
    esac
    shift
done

select_target "$target"
reports_dir
make_temp_dir
file=$REPORTS/blesk-log-$(timestamp).txt

step "Сохраняю журнал blesk $TARGET_FROM"
app_pids=$("$ADB" -s "$SERIAL" shell pidof "$BLESK_PACKAGE" 2>/dev/null | tr -d '\r' || true)
version=$(installed_version)
"$ADB" -s "$SERIAL" logcat -d -v threadtime -b main -b system >"$BLESK_TMP/main.txt" 2>/dev/null ||
    fail "Не удалось прочитать журнал $TARGET_FROM. Отключи кабель, подключи снова и запусти скрипт ещё раз."
"$ADB" -s "$SERIAL" logcat -d -v threadtime -b crash >"$BLESK_TMP/crash.txt" 2>/dev/null || true
tr -d '\r' <"$BLESK_TMP/main.txt" >"$BLESK_TMP/main.lf"
tr -d '\r' <"$BLESK_TMP/crash.txt" >"$BLESK_TMP/crash.lf"

# threadtime lines are "date time pid tid level tag: message". Besides the app's current process the
# log keeps earlier ones: those that ActivityManager started as app.blesk and those that crashed.
# ActivityManager's own lines about the app tell when it was killed or stopped responding.
app_lines() {
    awk -v current="$app_pids" '
        function tag(  t) { t = $6; sub(/:$/, "", t); return t }
        BEGIN { n = split(current, list, " "); for (i = 1; i <= n; i++) pid[list[i]] = 1 }
        NR == FNR {
            if (match($0, /Start proc [0-9]+:app\.blesk(:|\/)/)) {
                started = substr($0, RSTART + 11, RLENGTH - 11)
                sub(/:.*/, "", started)
                pid[started] = 1
            }
            if (tag() == "AndroidRuntime" && index($0, "app.blesk")) pid[$3] = 1
            next
        }
        tag() ~ /^blesk/ || ($3 in pid) || (tag() == "ActivityManager" && index($0, "app.blesk"))
    ' "$1" "$1"
}

# A crash entry is written by one process (the app itself, or crash_dump for a native crash), so
# every line of a process that mentions the app belongs to the app's crashes.
crash_lines() {
    awk 'NR == FNR { if (index($0, "app.blesk")) pid[$3] = 1; next } ($3 in pid)' "$1" "$1"
}

app_log=$(app_lines "$BLESK_TMP/main.lf")
crash_log=$(crash_lines "$BLESK_TMP/crash.lf")
{
    printf 'Журнал blesk для отчёта об ошибке\n'
    printf 'Сохранён: %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf 'Устройство: %s %s (Android %s, API %s)\n' "$(device_prop ro.product.manufacturer)" \
        "$(device_prop ro.product.model)" "$(device_prop ro.build.version.release)" "$(device_prop ro.build.version.sdk)"
    printf 'blesk: %s\n' "${version:-не установлен}"
    printf 'Процесс blesk: %s\n' "${app_pids:-не запущен}"
    printf '\n===== Журнал (main, system) =====\n'
    printf '%s\n' "${app_log:-(пусто)}"
    printf '\n===== Сбои (crash) =====\n'
    printf '%s\n' "${crash_log:-(пусто)}"
} >"$file"

ok "Журнал сохранён: $file"
info "Приложи этот файл к отчёту об ошибке вместе со скриншотом или видео (screenrecord)."
