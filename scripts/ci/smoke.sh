#!/usr/bin/env bash
# The release APK on an emulator, as a person would meet it: installs, starts at the welcome screen, signs
# up by invite on the test server of scripts/ci/test-server.sh, which must be up, and goes through every
# setup step to the chats. The emulator reaches the server through `adb reverse` on 127.0.0.1:8443, the
# address in the server's certificate. Screenshots of the steps and the time of the cold start go to the
# directory given, for the CI artifact.
#
#   scripts/ci/smoke.sh <blesk.apk> <screenshot dir>
# -E: the trap below also catches a step that fails inside ui().
set -Eeuo pipefail

apk=${1:?usage: smoke.sh <blesk.apk> <screenshot dir>}
shots=${2:?usage: smoke.sh <blesk.apk> <screenshot dir>}
root=$(cd "$(dirname "$0")/../.." && pwd)
server="$root/scripts/ci/test-server.sh"
package=app.blesk
mkdir -p "$shots"

pin=$(openssl x509 -in "$root/deploy/certs/blesk.crt" -pubkey -noout | openssl pkey -pubin -outform DER |
    openssl dgst -sha256 -binary | base64)
# Taken whole and cut here: head would stop reading, and pipefail would fail on the writer it cut off.
invites=$("$server" invites)
invite=${invites%%$'\n'*}
[[ -n $invite ]] || { echo "the test server has no bootstrap invites left" >&2; exit 1; }
username="smoke_$(date +%s)"
step=0

ui() { python3 "$root/scripts/ci/ui.py" "$@"; }
shot() {
    step=$((step + 1))
    ui screenshot "$shots/$(printf '%02d' "$step")_$1.png"
}
fail() {
    shot failed || true
    adb exec-out uiautomator dump /dev/tty >"$shots/screen.xml" 2>/dev/null || true
    adb logcat -d -s blesk:V AndroidRuntime:E >"$shots/logcat.txt" 2>&1 || true
    echo "smoke test failed: $1" >&2
    exit 1
}
trap 'fail "step $step"' ERR

adb wait-for-device
# Text goes in through `input text`, and an on-screen keyboard would cover the fields below the focused
# one. Android falls back to any input method left, the voice one included, so all of them go, with their
# apps. adb reads stdin, hence the list is taken whole first.
mapfile -t imes < <(adb shell ime list -a -s | tr -d '\r')
for ime in "${imes[@]}"; do
    adb shell ime disable "$ime" </dev/null >/dev/null 2>&1 || true
    adb shell pm disable-user --user 0 "${ime%%/*}" </dev/null >/dev/null 2>&1 || true
done
left=$(adb shell ime list -s | tr -d '\r')
[[ -z $left ]] || { echo "input methods still on: $left" >&2; exit 1; }
adb reverse tcp:8443 tcp:443
# Crash and «not responding» dialogs of other apps would cover the screen being read.
adb shell settings put global hide_error_dialogs 1
adb install -r "$apk"
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# A cold first start: the splash holds until the start screen is read, so this is the time to the welcome screen.
adb shell am start -W -n "$package/.MainActivity" | tr -d '\r' | tee "$shots/start.txt"

ui wait "Создать аккаунт" 90
shot welcome
ui tap "Другой сервер"
ui type "Адрес сервера" "blesk://server?url=https://127.0.0.1:8443&pin=sha256/$pin"
shot server
# A step's buttons stay at the foot of the screen, below what `tap` reaches; `tab` taps there.
ui tab "Готово"
# Back on the welcome screen once the test server has answered; then its invite step.
ui tap "Создать аккаунт"
ui type "Код или ссылка" "$invite"
shot invite
ui tab "Дальше"
ui type "Имя пользователя" "$username"
shot username
ui tab "Создать аккаунт"
ui wait "Как тебя называть" 120
ui tab "Пропустить"
# The phrase screen cannot be captured, so it has no shot.
ui wait "Фраза восстановления"
ui tap "Позже"
ui wait "Код-пароль"
ui tab "Не сейчас"
ui wait "Тема"
ui tab "Дальше"
ui wait "Язык блеска"
shot glint
ui tab "В чаты"
ui wait "Позвать друга" 60
shot chats

# Signed up means both sides agree: the server holds the name and the app logged that it moved to it.
taken=$(curl -fsS --cacert "$root/deploy/certs/blesk.crt" "https://127.0.0.1/v1/usernames/$username" | jq -r .available || true)
[[ $taken == false ]] || fail "the server has no account $username"
registered=
for _ in $(seq 30); do
    journal=$(adb logcat -d -s blesk:I)
    [[ $journal == *ServerRegistered* ]] && registered=1 && break
    sleep 2
done
[[ -n $registered ]] || fail "the app never logged ServerRegistered"
echo "smoke test passed: @$username signed up through the welcome screen; cold start $(sed -n 's/^TotalTime: //p' "$shots/start.txt") ms"
