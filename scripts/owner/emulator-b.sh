#!/usr/bin/env bash
# Starts the Android emulator as the second phone «Б» and installs the newest blesk build on it.
# The first run creates the emulator: a Pixel with Android 14 (API 34) and Google APIs.
#
#   bash scripts/owner/emulator-b.sh [--force]
#
# --force: as in install-latest, replace a copy of blesk that cannot be updated.
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

API_LEVEL=34
BOOT_TIMEOUT=240
STUDIO_SDK_TOOLS="Открой Android Studio → Settings → Languages & Frameworks → Android SDK → вкладка SDK Tools,"

force=0
while [[ $# -gt 0 ]]; do
    case $1 in
        --force | -f) force=1 ;;
        *) fail "Неизвестный параметр: $1. Можно только --force." ;;
    esac
    shift
done

find_sdk() {
    local dir
    for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
        "$(dirname "$(dirname "$ADB")")"; do
        if [[ -n $dir && ( -x $dir/emulator/emulator || -x $dir/cmdline-tools/latest/bin/sdkmanager ) ]]; then
            SDK=$dir
            return 0
        fi
    done
    fail "Android SDK с эмулятором не найден." "$STUDIO_SDK_TOOLS" \
        "отметь Android Emulator и Android SDK Command-line Tools (latest) → Apply. Потом запусти скрипт снова."
}

# Apple Silicon reports x86_64 to a terminal running under Rosetta, hence the sysctl.
image_abi() {
    local machine
    machine=$(uname -m)
    if [[ $machine == arm64 || $machine == aarch64 || $(sysctl -n hw.optional.arm64 2>/dev/null || true) == 1 ]]; then
        printf 'arm64-v8a\n'
    else
        printf 'x86_64\n'
    fi
}

java_works() { [[ -x "$1/bin/java" ]] && "$1/bin/java" -version >/dev/null 2>&1; }

# sdkmanager needs Java 17 or newer; Android Studio brings its own, and macOS has a java stub
# that only offers to install one, so plain "java" is tried last and actually run.
find_java() {
    local home
    for home in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        /opt/android-studio/jbr "$HOME/android-studio/jbr" "${JAVA_HOME:-}"; do
        if [[ -n "$home" ]] && java_works "$home"; then
            export JAVA_HOME=$home
            return 0
        fi
    done
    if home=$(/usr/libexec/java_home 2>/dev/null) && java_works "$home"; then
        export JAVA_HOME=$home
        return 0
    fi
    if java -version >/dev/null 2>&1; then return 0; fi
    fail "Java не найдена, а без неё не создать эмулятор. Установи Android Studio (Java входит в неё) и запусти скрипт снова."
}

yes_lines() {
    local i=0
    while [[ "$i" -lt 50 ]]; do
        printf 'y\n'
        i=$((i + 1))
    done
}

device_profile() {
    local ids id
    ids=$("$1" list device -c 2>/dev/null | tr -d '\r' || true)
    for id in pixel_7 pixel_6 pixel_5 pixel_4 pixel; do
        if grep -qx "$id" <<<"$ids"; then
            printf '%s\n' "$id"
            return 0
        fi
    done
    printf 'pixel\n'
}

avd_home() {
    if [[ -n "${ANDROID_AVD_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_AVD_HOME"
    elif [[ -n "${ANDROID_USER_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_USER_HOME/avd"
    else
        printf '%s\n' "$HOME/.android/avd"
    fi
}

set_ini_value() {
    local file=$1 key=$2 value=$3
    grep -v "^${key}[[:space:]]*=" "$file" >"$file.tmp" || true
    printf '%s=%s\n' "$key" "$value" >>"$file.tmp"
    mv "$file.tmp" "$file"
}

# avdmanager leaves the laptop keyboard and the graphics card unused; both make «Б» painful to use.
tune_avd() {
    local config
    config=$(avd_home)/$BLESK_AVD.avd/config.ini
    [[ -f "$config" ]] || return 0
    set_ini_value "$config" hw.keyboard yes
    set_ini_value "$config" hw.gpu.enabled yes
    set_ini_value "$config" hw.gpu.mode auto
}

create_avd() {
    local tools=$SDK/cmdline-tools/latest/bin image device
    if [[ ! -x $tools/sdkmanager || ! -x $tools/avdmanager ]]; then
        fail "Чтобы создать эмулятор «Б», нужны Android SDK Command-line Tools." "$STUDIO_SDK_TOOLS" \
            "отметь Android SDK Command-line Tools (latest) → Apply → OK. Потом запусти скрипт снова."
    fi
    find_java
    image="system-images;android-$API_LEVEL;google_apis;$(image_abi)"
    step "Создаю эмулятор «Б»: Pixel, Android 14"
    info "Скачиваю Android 14 для эмулятора — около 1,5 ГБ, это может занять 10–20 минут."
    yes_lines | "$tools/sdkmanager" --licenses >/dev/null ||
        fail "Не удалось принять лицензии Android SDK. Запусти скрипт снова."
    yes_lines | "$tools/sdkmanager" --install "$image" emulator platform-tools ||
        fail "Не удалось скачать Android 14 для эмулятора." \
            "Проверь интернет и свободное место (нужно около 10 ГБ) и запусти скрипт снова."
    device=$(device_profile "$tools/avdmanager")
    printf 'no\n' | "$tools/avdmanager" create avd --name "$BLESK_AVD" --package "$image" --device "$device" ||
        fail "Не удалось создать эмулятор «Б». Пришли мне скриншот этого окна."
    tune_avd
    ok "Эмулятор «Б» создан."
}

start_emulator() {
    local tmp=${TMPDIR:-/tmp}
    EMULATOR_LOG=${tmp%/}/blesk-emulator.log
    step "Запускаю эмулятор «Б»"
    nohup "$SDK/emulator/emulator" -avd "$BLESK_AVD" -no-boot-anim >"$EMULATOR_LOG" 2>&1 &
    EMULATOR_PID=$!
}

emulator_failed() {
    warn "Эмулятор закрылся, не успев загрузиться. Последние строки его журнала:"
    tail -n 15 "$EMULATOR_LOG" || true
    warn "Проверка ускорения:"
    "$SDK/emulator/emulator" -accel-check 2>&1 || true
    fail "Пришли мне скриншот этого окна. Журнал эмулятора целиком: $EMULATOR_LOG"
}

boot_completed() { "$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true; }

wait_for_boot() {
    local started=$SECONDS serial='' elapsed note=15
    info "Жду, пока Android загрузится (до 4 минут)."
    while :; do
        elapsed=$((SECONDS - started))
        if [[ -n "${EMULATOR_PID:-}" ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then emulator_failed; fi
        if [[ -z "$serial" ]]; then serial=$(blesk_emulator_serial); fi
        if [[ -n $serial && $(boot_completed "$serial") == 1 ]]; then break; fi
        if [[ "$elapsed" -ge "$BOOT_TIMEOUT" ]]; then
            fail "Эмулятор не загрузился за 4 минуты." \
                "Дождись, пока на его экране появится рабочий стол, и запусти скрипт снова: он просто поставит blesk."
        fi
        if [[ "$elapsed" -ge "$note" ]]; then
            info "…прошло $elapsed с"
            note=$((note + 15))
        fi
        sleep 3
    done
    SERIAL=$serial
    use_emulator_labels
    "$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
    ok "Эмулятор «Б» загрузился."
}

find_adb
start_adb
find_sdk
if [[ -n "$(blesk_emulator_serial)" ]]; then
    step "Эмулятор «Б» уже запущен"
else
    avds=''
    if [[ -x "$SDK/emulator/emulator" ]]; then avds=$("$SDK/emulator/emulator" -list-avds 2>/dev/null | tr -d '\r' || true); fi
    if ! grep -qx "$BLESK_AVD" <<<"$avds"; then create_avd; fi
    [[ -x "$SDK/emulator/emulator" ]] || fail "Эмулятор не установился. Запусти скрипт снова."
    start_emulator
fi
wait_for_boot
install_latest "$force" "bash scripts/owner/emulator-b.sh --force"
info "Эмулятор можно не закрывать между проверками. Выключить его — закрыть окно эмулятора."
