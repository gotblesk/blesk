#!/usr/bin/env bash
# Records the phone's screen (or the emulator «Б»'s) into a video file for a bug report.
#
#   bash scripts/owner/screenrecord.sh [--emulator]
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

REMOTE_FILE=/sdcard/blesk-record.mp4
LIMIT_SECONDS=180

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
file=$REPORTS/blesk-video-$(timestamp).mp4

stop_recording() {
    # SIGINT makes screenrecord finish the file properly; older toybox builds lack pkill -INT.
    # shellcheck disable=SC2016
    "$ADB" -s "$SERIAL" shell 'pkill -INT screenrecord || kill -INT $(pidof screenrecord)' >/dev/null 2>&1 || true
}

recording_on_device() {
    [[ -n "$("$ADB" -s "$SERIAL" shell pidof screenrecord 2>/dev/null | tr -d '\r\n' || true)" ]]
}

"$ADB" -s "$SERIAL" shell rm -f "$REMOTE_FILE" >/dev/null 2>&1 || true
step "Записываю экран $TARGET_FROM"
"$ADB" -s "$SERIAL" shell screenrecord --time-limit "$LIMIT_SECONDS" "$REMOTE_FILE" &
recorder=$!
started=$SECONDS
sleep 1
if ! kill -0 "$recorder" 2>/dev/null; then
    wait "$recorder" || true
    fail "Запись не началась. Разблокируй экран и запусти скрипт снова." \
        "Если не поможет, пришли мне скриншот этого окна."
fi

# Ctrl+C stops the recording just like Enter does, so the video is kept either way.
stop_requested=0
trap 'stop_requested=1' INT
info "Запись идёт. Нажми Enter, чтобы остановить (не дольше 3 минут)"
while [[ "$stop_requested" -eq 0 ]] && kill -0 "$recorder" 2>/dev/null; do
    status=0
    read -r -t 1 _ || status=$?
    # Above 128 the read timed out; 0 is Enter and 1 means there is no keyboard input at all.
    if [[ "$status" -le 128 ]]; then break; fi
done
trap - INT
if ! kill -0 "$recorder" 2>/dev/null; then
    if ((SECONDS - started >= LIMIT_SECONDS - 5)); then
        info "Прошло 3 минуты, запись остановилась сама."
    else
        warn "Запись прервалась раньше времени. Сохраняю то, что успело записаться."
    fi
fi

step "Останавливаю запись"
stop_recording
waited=0
while kill -0 "$recorder" 2>/dev/null && [[ "$waited" -lt 15 ]]; do
    sleep 1
    waited=$((waited + 1))
done
kill "$recorder" 2>/dev/null || true
wait "$recorder" 2>/dev/null || true
waited=0
while recording_on_device && [[ "$waited" -lt 10 ]]; do
    sleep 1
    waited=$((waited + 1))
done

"$ADB" -s "$SERIAL" pull "$REMOTE_FILE" "$file" >/dev/null 2>&1 ||
    fail "Не удалось забрать видео $TARGET_FROM. Отключи кабель, подключи снова и запусти скрипт ещё раз."
"$ADB" -s "$SERIAL" shell rm -f "$REMOTE_FILE" >/dev/null 2>&1 || true

ok "Видео сохранено: $file"
info "Приложи его к отчёту об ошибке вместе с журналом (logs)."
