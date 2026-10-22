#!/usr/bin/env bash
# Caps what one address may do to the web ports, in front of Caddy: open connections at once and new
# connections per second. Docker sends published ports through its own chains, past ufw, so the rules
# go into DOCKER-USER, which Docker keeps for them. Run by blesk-connlimit.service (scripts/deploy.sh)
# each time Docker starts; safe to run again.
#
# Generous on purpose: a mobile operator puts many people behind one address, and a phone keeps two
# or three connections. The server's own per-address limit catches the rest.
set -euo pipefail

CONNECTIONS=200
NEW_PER_SECOND=50
BURST=200
rule_comment=blesk-connlimit

apply() {
    local tables=$1
    command -v "$tables" >/dev/null || return 0
    "$tables" -n -L DOCKER-USER >/dev/null 2>&1 || return 0
    # Ours are recognised by their comment and replaced as a whole, so a change of numbers takes effect.
    # They go by number: a rule printed by -S does not always read back as the same rule for -D.
    local number
    while number=$("$tables" -n -L DOCKER-USER --line-numbers | awk -v tag="/* $rule_comment */" 'index($0, tag) { print $1; exit }') &&
        [[ -n $number ]]; do
        "$tables" -D DOCKER-USER "$number"
    done
    "$tables" -I DOCKER-USER 1 -p tcp -m multiport --dports 80,443 -m connlimit --connlimit-above "$CONNECTIONS" \
        -m comment --comment "$rule_comment" -j REJECT --reject-with tcp-reset
    "$tables" -I DOCKER-USER 2 -p tcp --syn -m multiport --dports 80,443 \
        -m hashlimit --hashlimit-above "$NEW_PER_SECOND/second" --hashlimit-burst "$BURST" --hashlimit-mode srcip \
        --hashlimit-name "${rule_comment}-$tables" -m comment --comment "$rule_comment" -j DROP
}

apply iptables
apply ip6tables
