#!/usr/bin/env bash
# Checks from outside that a deployed server works: the API is healthy, the site, its www redirect
# and the APK download answer, Android can verify the app links, and every certificate stays valid
# for at least two more weeks. Retries for about three minutes, since right after a deploy Caddy may
# still be getting certificates.
#
#   scripts/ci/check-live.sh api.blesk.su blesk.su
# The checks below run only through check(), which shellcheck cannot follow.
# shellcheck disable=SC2329
set -euo pipefail

api=${1:?usage: check-live.sh <api domain> <site domain>}
site=${2:?usage: check-live.sh <api domain> <site domain>}
deadline=$((SECONDS + ${BLESK_CHECK_SECONDS:-180}))
min_valid_seconds=$((14 * 24 * 3600))
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
results=()
failed=0

api_healthy() { [[ $(curl -fsS --max-time 10 "https://$api/health" | jq -r .status) == ok ]]; }

site_up() {
    curl -fsS --max-time 15 -o "$tmp/index.html" "https://$site/" && grep -qF 'Блестящий мессенджер' "$tmp/index.html"
}

www_redirects() {
    local answer
    answer=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{redirect_url}' "https://www.$site/")
    [[ $answer == "301 https://$site/" || $answer == "308 https://$site/" ]]
}

download_served() {
    local answer
    answer=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{redirect_url}' "https://$site/download")
    [[ $answer == "302 https://$site/download/blesk.apk" ]] || return 1
    curl -fsSI --max-time 10 -o "$tmp/apk-headers" "https://$site/download/blesk.apk" &&
        grep -qi '^content-disposition: attachment' "$tmp/apk-headers"
}

# Android verifies app links only from a 200 with Content-Type application/json and no redirect.
asset_links() {
    local type
    type=$(curl -fsS --max-time 10 -o "$tmp/assetlinks.json" -w '%{content_type}' "https://$site/.well-known/assetlinks.json")
    [[ $type == application/json* ]] && jq -e 'any(.[]; .target.package_name == "app.blesk")' "$tmp/assetlinks.json" >/dev/null
}

certificate_valid() {
    openssl s_client -connect "$1:443" -servername "$1" -verify_hostname "$1" -verify_return_error </dev/null >"$tmp/tls" &&
        openssl x509 -in "$tmp/tls" -noout -checkend "$min_valid_seconds" >/dev/null
}

check() {
    local name=$1
    shift
    until "$@" 2>/dev/null; do
        if ((SECONDS >= deadline)); then
            echo "::error::$name: failed"
            results+=("| $name | failed |")
            failed=1
            return 0
        fi
        sleep 10
    done
    echo "$name: ok"
    results+=("| $name | ok |")
}

check "https://$api/health answers ok" api_healthy
check "https://$site/ shows the home page" site_up
check "https://www.$site/ redirects to https://$site/" www_redirects
check "https://$site/download gives the APK as a file" download_served
check "https://$site/.well-known/assetlinks.json lists app.blesk" asset_links
for host in "$site" "www.$site" "$api"; do
    check "certificate of $host valid for 14 more days" certificate_valid "$host"
done

if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    {
        echo "### Live checks"
        echo
        echo "| Check | Result |"
        echo "|---|---|"
        printf '%s\n' "${results[@]}"
    } >>"$GITHUB_STEP_SUMMARY"
fi
exit "$failed"
