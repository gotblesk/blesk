# shellcheck shell=bash disable=SC2034  # the scripts that source this file read its variables
# Helpers for the owner's laptop scripts in this folder; they source it, it is never run directly.
# macOS still ships bash 3.2, so nothing newer is used here or in the scripts: no mapfile, no
# associative arrays, no case conversion in expansions, no fractional read timeouts and no arrays
# that may be empty (bash before 4.4 calls an empty array unbound under set -u).

BLESK_PACKAGE=app.blesk
# Each release carries hundreds of screenshots, so a page of a few releases is already megabytes.
BLESK_RELEASES_API='https://api.github.com/repos/gotblesk/blesk/releases?per_page=3'
BLESK_AVD=blesk-b
BLESK_SERVER_IP=94.228.175.178

if [[ -t 1 ]]; then
    STYLE_STEP=$'\033[1m' STYLE_OK=$'\033[32m' STYLE_WARN=$'\033[33m' STYLE_END=$'\033[0m'
else
    STYLE_STEP='' STYLE_OK='' STYLE_WARN='' STYLE_END=''
fi
if [[ -t 2 ]]; then
    STYLE_ERROR=$'\033[31m' STYLE_ERROR_END=$'\033[0m'
else
    STYLE_ERROR='' STYLE_ERROR_END=''
fi

# Every argument is printed as a line of its own.
print_styled() {
    local style=$1 end='' line
    shift
    if [[ -n "$style" ]]; then end=$STYLE_END; fi
    for line in "$@"; do printf '%s%s%s\n' "$style" "$line" "$end"; done
}
step() {
    printf '\n'
    print_styled "$STYLE_STEP" "$@"
}
info() { print_styled '' "$@"; }
ok() { print_styled "$STYLE_OK" "$@"; }
warn() { print_styled "$STYLE_WARN" "$@"; }
fail() {
    local line
    printf '\n' >&2
    for line in "$@"; do printf '%s%s%s\n' "$STYLE_ERROR" "$line" "$STYLE_ERROR_END" >&2; done
    exit 1
}

timestamp() { date +%Y-%m-%d_%H-%M-%S; }

# Logs and videos for a bug report land together on the desktop, where they are easy to attach.
reports_dir() {
    local base=$HOME
    if [[ -d "$HOME/Desktop" ]]; then base=$HOME/Desktop; fi
    REPORTS=$base/blesk-reports
    mkdir -p "$REPORTS" || fail "Не удалось создать папку $REPORTS."
}

make_temp_dir() {
    BLESK_TMP=$(mktemp -d "${TMPDIR:-/tmp}/blesk.XXXXXX") || fail "Не удалось создать временную папку."
    trap 'rm -rf "$BLESK_TMP"' EXIT
}

find_adb() {
    local dir
    if ADB=$(command -v adb 2>/dev/null); then return 0; fi
    for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [[ -n $dir && -x $dir/platform-tools/adb ]]; then
            ADB=$dir/platform-tools/adb
            return 0
        fi
    done
    for dir in "$HOME/platform-tools" "$HOME/Downloads/platform-tools" "$HOME/Desktop/platform-tools"; do
        if [[ -x "$dir/adb" ]]; then
            ADB=$dir/adb
            return 0
        fi
    done
    fail "adb не найден. Это программа, через которую ноутбук ставит приложения на телефон." \
        "Открой Android Studio → Settings → Languages & Frameworks → Android SDK → вкладка SDK Tools," \
        "отметь Android SDK Platform-Tools → Apply. Потом запусти скрипт снова."
}

start_adb() {
    "$ADB" start-server >/dev/null 2>&1 ||
        fail "adb не запускается. Закрой Android Studio и другие программы для телефонов и запусти скрипт снова."
}

# One "serial<TAB>state" line per device; the header and adb's own notices have no tab.
list_devices() {
    "$ADB" devices 2>/dev/null | tr -d '\r' | awk -F '\t' 'NF >= 2 && $1 != "" { print $1 "\t" $2 }'
}

use_phone_labels() { TARGET_TO='на телефон' TARGET_FROM='с телефона' TARGET_ON='на телефоне'; }
use_emulator_labels() { TARGET_TO='на эмулятор «Б»' TARGET_FROM='с эмулятора «Б»' TARGET_ON='на эмуляторе «Б»'; }

select_phone() {
    local devices ready states count
    devices=$(list_devices | awk -F '\t' '$1 !~ /^emulator-/')
    ready=$(awk -F '\t' '$2 == "device" { print $1 }' <<<"$devices")
    states=$(awk -F '\t' '{ print $2 }' <<<"$devices")
    count=$(grep -c . <<<"$ready" || true)
    if [[ "$count" -eq 1 ]]; then
        SERIAL=$ready
        use_phone_labels
        return 0
    fi
    if [[ "$count" -gt 1 ]]; then
        fail "Подключено несколько телефонов. Оставь подключённым один и запусти скрипт снова."
    fi
    case $states in
        *unauthorized*)
            fail "Телефон подключён, но не разрешил отладку." \
                "Разблокируй телефон и нажми «Разрешить» в окне «Разрешить отладку по USB?»." \
                "Если окна нет, отключи кабель, подключи снова и запусти скрипт ещё раз."
            ;;
        *offline*) fail "Телефон не отвечает. Отключи кабель, подключи снова и запусти скрипт ещё раз." ;;
        *"no permissions"*) fail "Системе не хватает прав на телефон. Отключи кабель, подключи снова и запусти скрипт ещё раз." ;;
    esac
    fail "Телефон не найден. Подключи кабель и разреши отладку по USB на телефоне" \
        "(Настройки → Для разработчиков → Отладка по USB)." \
        "Кабель должен передавать данные: некоторые кабели только заряжают."
}

# The serial of the running emulator «Б», or nothing when it is not running.
blesk_emulator_serial() {
    local serial name
    for serial in $(list_devices | awk -F '\t' '$1 ~ /^emulator-/ { print $1 }'); do
        name=$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | sed -n 1p || true)
        if [[ "$name" = "$BLESK_AVD" ]]; then
            printf '%s\n' "$serial"
            return 0
        fi
    done
}

select_emulator() {
    local serial running
    serial=$(blesk_emulator_serial)
    if [[ -z "$serial" ]]; then
        running=$(list_devices | awk -F '\t' '$1 ~ /^emulator-/ && $2 == "device" { print $1 }')
        if [[ "$(grep -c . <<<"$running" || true)" -eq 1 ]]; then serial=$running; fi
    fi
    if [[ -z "$serial" ]]; then
        fail "Эмулятор «Б» не запущен. Запусти его: bash scripts/owner/emulator-b.sh"
    fi
    SERIAL=$serial
    use_emulator_labels
}

# $1 is "phone" or "emulator".
select_target() {
    find_adb
    start_adb
    if [[ "$1" = emulator ]]; then select_emulator; else select_phone; fi
}

device_prop() { "$ADB" -s "$SERIAL" shell getprop "$1" 2>/dev/null | tr -d '\r' || true; }

# "1.0.0-rc3-build.201 (код 201)" for the copy installed on $SERIAL, nothing when there is none.
installed_version() {
    local dump name code
    dump=$("$ADB" -s "$SERIAL" shell dumpsys package "$BLESK_PACKAGE" 2>/dev/null | tr -d '\r' || true)
    name=$(sed -n 's/^[[:space:]]*versionName=//p' <<<"$dump" | sed -n 1p)
    code=$(sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' <<<"$dump" | sed -n 1p)
    if [[ -n "$name" ]]; then printf '%s (код %s)\n' "$name" "$code"; fi
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{ print $1 }'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{ print $1 }'
    else
        openssl dgst -sha256 -r "$1" | awk '{ print $1 }'
    fi
}

lowercase() { tr 'ABCDEF' 'abcdef' <<<"$1"; }

is_sha256() {
    case $1 in *[!0-9a-fA-F]* | '') return 1 ;; esac
    [[ "${#1}" -eq 64 ]]
}

# python3 comes with the command line tools that Git needs on macOS and parses megabytes at once;
# perl's JSON::PP is on every macOS but slow, so it is the fallback.
find_json_tool() {
    if python3 -I -c 'import json' >/dev/null 2>&1; then
        JSON_TOOL=python3
    elif perl -MJSON::PP -e 1 >/dev/null 2>&1; then
        JSON_TOOL=perl
    else
        fail "Нет ни perl, ни python3, а без них не прочитать список сборок GitHub."
    fi
}

# Reads the GitHub releases list in $1 and prints "tag|apk url|apk size|release.json url|body sha"
# for the newest published release that has blesk.apk; exits with 3 when there is none.
pick_release() {
    if [[ "$JSON_TOOL" = perl ]]; then
        perl -MJSON::PP - "$1" <<'PERL'
use strict;
use warnings;
open my $fh, '<:raw', $ARGV[0] or die "$!\n";
my $releases = JSON::PP::decode_json(do { local $/; <$fh> });
my ($best, $apk, $meta);
for my $release (@$releases) {
    next if $release->{draft} || !$release->{published_at};
    my @assets = @{ $release->{assets} || [] };
    my ($file) = grep { ($_->{name} // '') eq 'blesk.apk' } @assets;
    next if !$file || ($best && $release->{published_at} le $best->{published_at});
    ($best, $apk) = ($release, $file);
    ($meta) = grep { ($_->{name} // '') eq 'release.json' } @assets;
}
exit 3 if !$best;
my ($sha) = ($best->{body} // '') =~ /SHA-256 APK:\s*`([0-9A-Fa-f]{64})`/;
print join('|', $best->{tag_name}, $apk->{browser_download_url}, $apk->{size},
    $meta ? $meta->{browser_download_url} : '', $sha // ''), "\n";
PERL
    else
        python3 -I - "$1" <<'PYTHON'
import json
import re
import sys

with open(sys.argv[1], 'rb') as handle:
    releases = json.load(handle)
best = apk = meta = None
for release in releases:
    if release.get('draft') or not release.get('published_at'):
        continue
    assets = {asset.get('name'): asset for asset in release.get('assets') or []}
    if 'blesk.apk' not in assets or (best and release['published_at'] <= best['published_at']):
        continue
    best, apk, meta = release, assets['blesk.apk'], assets.get('release.json')
if best is None:
    sys.exit(3)
sha = re.search(r'SHA-256 APK:\s*`([0-9A-Fa-f]{64})`', best.get('body') or '')
print('|'.join([best['tag_name'], apk['browser_download_url'], str(apk['size']),
                meta['browser_download_url'] if meta else '', sha.group(1) if sha else '']))
PYTHON
    fi
}

# release.json is a flat object written by scripts/ci/release-json.sh, one key per line or not.
json_string() {
    sed -n 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$2" | sed -n 1p
}

# Sets REL_TAG, REL_VERSION, REL_APK_URL, REL_APK_SIZE and REL_SHA, the checksum the release build
# published: from release.json when the release has one, else from the line in the release notes.
fetch_latest_release() {
    local list=$BLESK_TMP/releases.json code picked json_url body_sha
    step "Ищу последнюю сборку blesk на GitHub"
    find_json_tool
    code=$(curl -sSL -m 120 -H 'Accept: application/vnd.github+json' -o "$list" -w '%{http_code}' "$BLESK_RELEASES_API") ||
        fail "Не удалось связаться с GitHub. Проверь интернет и запусти скрипт снова."
    case $code in
        200) ;;
        403 | 429) fail "GitHub временно ограничил запросы с твоего адреса. Подожди час и запусти скрипт снова." ;;
        *) fail "GitHub ответил ошибкой $code. Попробуй ещё раз через несколько минут." ;;
    esac
    picked=$(pick_release "$list") || fail "На GitHub пока нет опубликованной сборки с blesk.apk."
    IFS='|' read -r REL_TAG REL_APK_URL REL_APK_SIZE json_url body_sha <<<"$picked"
    REL_VERSION=$REL_TAG
    if [[ -n "$json_url" ]]; then
        curl -fsSL -m 120 --retry 3 -o "$BLESK_TMP/release.json" "$json_url" ||
            fail "Не удалось скачать описание сборки $REL_TAG. Проверь интернет и запусти скрипт снова."
        REL_SHA=$(json_string apkSha256 "$BLESK_TMP/release.json")
        REL_VERSION=$(json_string version "$BLESK_TMP/release.json")
        REL_VERSION=${REL_VERSION:-$REL_TAG}
    else
        REL_SHA=$body_sha
    fi
    is_sha256 "$REL_SHA" ||
        fail "У сборки $REL_TAG нет контрольной суммы APK. Ставить такой файл небезопасно, ничего не меняю."
    info "Последняя сборка: $REL_VERSION"
}

# Sets APK to the downloaded file once its SHA-256 matches REL_SHA.
download_apk() {
    local size_note='' actual expected
    case $REL_APK_SIZE in
        '' | *[!0-9]*) ;;
        *) size_note=", $(((REL_APK_SIZE + 524288) / 1048576)) МБ" ;;
    esac
    APK=$BLESK_TMP/blesk.apk
    step "Скачиваю blesk.apk$size_note"
    curl -fL --retry 3 --progress-bar -o "$APK" "$REL_APK_URL" ||
        fail "Не удалось скачать APK. Проверь интернет и запусти скрипт снова."
    actual=$(lowercase "$(sha256_of "$APK")")
    expected=$(lowercase "$REL_SHA")
    if [[ "$actual" != "$expected" ]]; then
        rm -f "$APK"
        fail "Контрольная сумма APK не совпала: файл повреждён или подменён. Ставить его нельзя." \
            "Ожидалась:  $expected" "Получилась: $actual" "Запусти скрипт ещё раз; если повторится — напиши мне."
    fi
    ok "Контрольная сумма совпала: это тот самый файл, который собрал GitHub."
}

explain_conflict() {
    local reason=$1 rerun=$2
    if [[ "$reason" = INSTALL_FAILED_VERSION_DOWNGRADE ]]; then
        warn "Сейчас $TARGET_ON стоит сборка blesk новее, чем последняя на GitHub."
    else
        warn "Сейчас $TARGET_ON стоит blesk, подписанный другим ключом, и новая сборка не встаёт поверх."
    fi
    fail "Поставить её можно, только удалив старую копию вместе с данными:" \
        "аккаунтом, чатами и ключами на этом устройстве." "Если они не нужны, запусти скрипт ещё раз так:" "  $rerun"
}

install_succeeded() { grep -q '^Success' <<<"$1"; }

reinstall_apk() {
    local output
    warn "Удаляю старую копию blesk $TARGET_FROM вместе с её данными."
    "$ADB" -s "$SERIAL" uninstall "$BLESK_PACKAGE" >/dev/null 2>&1 || true
    output=$("$ADB" -s "$SERIAL" install "$APK" 2>&1 || true)
    if ! install_succeeded "$output"; then
        fail "Установка не удалась и после удаления старой копии:" "$(sed -n '$p' <<<"$output")"
    fi
}

# $1 is 1 when the owner agreed to replace a copy that cannot be updated, $2 the command for that.
install_apk() {
    local force=$1 rerun=$2 output reason
    step "Ставлю blesk $TARGET_TO"
    info "Если $TARGET_ON появится вопрос об установке, разреши её."
    output=$("$ADB" -s "$SERIAL" install -r "$APK" 2>&1 || true)
    if install_succeeded "$output"; then return 0; fi
    reason=$(awk 'match($0, /INSTALL_[A-Z_]+/) { print substr($0, RSTART, RLENGTH); exit }' <<<"$output")
    case $reason in
        INSTALL_FAILED_UPDATE_INCOMPATIBLE | INSTALL_FAILED_VERSION_DOWNGRADE)
            if [[ "$force" = 1 ]]; then
                reinstall_apk
            else
                explain_conflict "$reason" "$rerun"
            fi
            ;;
        INSTALL_FAILED_USER_RESTRICTED | INSTALL_FAILED_ABORTED)
            fail "Установку запретили $TARGET_ON. Если там было окно об установке — запусти скрипт снова и нажми «Установить»." \
                "На Xiaomi, Redmi и POCO ещё включи «Установка через USB» в разделе «Для разработчиков»."
            ;;
        INSTALL_FAILED_INSUFFICIENT_STORAGE) fail "Не хватает места $TARGET_ON. Освободи хотя бы 300 МБ и запусти скрипт снова." ;;
        *) fail "Установка не удалась:" "$(sed -n '$p' <<<"$output")" ;;
    esac
}

# The whole of install-latest; emulator-b reuses it once the emulator has booted.
install_latest() {
    local force=$1 rerun=$2 version
    make_temp_dir
    fetch_latest_release
    download_apk
    install_apk "$force" "$rerun"
    version=$(installed_version)
    ok "Готово: $TARGET_ON стоит blesk ${version:-$REL_VERSION}."
}

find_ssh() {
    command -v ssh >/dev/null 2>&1 ||
        fail "ssh не найден. На macOS он встроен; если его нет, выполни xcode-select --install и запусти скрипт снова."
}

# BLESK_SERVER may name another host, with or without "user@" in front.
server_target() {
    local server=${BLESK_SERVER:-$BLESK_SERVER_IP}
    case $server in
        *@*) SERVER_TARGET=$server ;;
        *) SERVER_TARGET=root@$server ;;
    esac
    SERVER_HOST=${SERVER_TARGET#*@}
    OWNER_KEY=$HOME/.ssh/blesk_owner
    DEPLOY_KEY=$HOME/.ssh/blesk_deploy
}
