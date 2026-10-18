#!/usr/bin/env bash
# Installs the newest blesk build from GitHub Releases on the phone connected by cable.
#
#   bash scripts/owner/install-latest.sh [--force]
#
# --force: when the phone has a copy that cannot be updated (signed by another key, or newer),
# remove it together with its data and install the latest build instead.
set -euo pipefail
here=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

force=0
while [[ $# -gt 0 ]]; do
    case $1 in
        --force | -f) force=1 ;;
        *) fail "Неизвестный параметр: $1. Можно только --force." ;;
    esac
    shift
done

select_target phone
install_latest "$force" "bash scripts/owner/install-latest.sh --force"
