#!/usr/bin/env bash
# Prints release.json for a built APK: what the site, scripts/deploy.sh and anyone checking a
# download need to know about a build without downloading it.
#
#   scripts/ci/release-json.sh out/blesk.apk > out/release.json
#
# Needs ANDROID_HOME with build-tools, GITHUB_RUN_NUMBER and GITHUB_SHA (set by GitHub Actions).
set -euo pipefail

fail() {
    echo "release-json: $*" >&2
    exit 1
}

android_release() {
    case $1 in
        26) echo 8.0 ;;
        27) echo 8.1 ;;
        28) echo 9 ;;
        29) echo 10 ;;
        30) echo 11 ;;
        31) echo 12 ;;
        32) echo 12L ;;
        33) echo 13 ;;
        34) echo 14 ;;
        35) echo 15 ;;
        36) echo 16 ;;
        *) fail "unknown Android version for minSdk $1" ;;
    esac
}

apk=${1:?usage: release-json.sh <apk>}
tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
badging=$("$tools/aapt2" dump badging "$apk")
version=$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<<"$badging")
version_code=$(sed -n "s/^package: .* versionCode='\([0-9]*\)'.*/\1/p" <<<"$badging")
min_sdk=$(sed -nE "s/^(min)?[sS]dkVersion:'([0-9]+)'.*/\2/p" <<<"$badging" | head -n 1)
# The app installs an update only if its key matches this one and the file matches apkSha256.
signer=$("$tools/apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1)

[[ -n $version && -n $version_code && -n $min_sdk ]] || fail "aapt2 printed no version or minSdk"
[[ $signer =~ ^[0-9a-f]{64}$ ]] || fail "apksigner printed no SHA-256 of the signing certificate"
min_android=$(android_release "$min_sdk")

jq -n \
    --arg version "$version" \
    --argjson versionCode "$version_code" \
    --arg tag "build-$GITHUB_RUN_NUMBER" \
    --arg commit "$GITHUB_SHA" \
    --argjson apkSize "$(stat -c %s "$apk")" \
    --arg apkSha256 "$(sha256sum "$apk" | cut -d' ' -f1)" \
    --arg signerSha256 "$signer" \
    --arg minAndroid "$min_android" \
    '{$version, $versionCode, $tag, $commit, $apkSize, $apkSha256, $signerSha256, $minAndroid}'
