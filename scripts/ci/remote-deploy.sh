#!/usr/bin/env bash
# Runs scripts/deploy.sh on the server over SSH, for the deploy job of build.yml and for deploy.yml.
# As root it runs the script itself (fetched from GitHub on a fresh server); as any other user it
# goes through the blesk-deploy command that scripts/server-bootstrap.sh allows in sudo.
#
# Environment: VPS_HOST, VPS_USER, VPS_SSH_KEY and BLESK_BRANCH; VPS_KNOWN_HOSTS, the server's host
# key (a known_hosts line, or a bare key as in /etc/ssh/ssh_host_ed25519_key.pub); BLESK_COMMIT,
# BLESK_DOMAIN, BLESK_SITE_DOMAIN, BLESK_RESTORE and the BACKUP_* settings are passed on when not empty.
set -euo pipefail

# sudo hands over only the names listed in DEPLOY_VARS of scripts/server-bootstrap.sh; a name added
# here has to be added there too.
FORWARDED=(
    BLESK_BRANCH BLESK_COMMIT BLESK_DOMAIN BLESK_SITE_DOMAIN BLESK_RESTORE
    BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY BACKUP_PASSWORD
)
WRAPPER=/usr/local/sbin/blesk-deploy

fail() {
    echo "::error::$*"
    exit 1
}

# Writes the host keys ssh may accept. The commands sent below carry the backup secrets, so the
# server has to prove it is the one in VPS_KNOWN_HOSTS; a key read off the network just now proves
# nothing, since whoever answers in the server's place hands out their own.
trust_host() {
    local file=$1 line
    if [[ -z ${VPS_KNOWN_HOSTS:-} ]]; then
        ssh-keyscan -T 15 -H "$VPS_HOST" >"$file" 2>/dev/null || true
        [[ -s $file ]] || fail "$VPS_HOST does not answer on SSH"
        echo "::warning::VPS_KNOWN_HOSTS is not set, so the host key of $VPS_HOST is taken unverified:" \
            "$(ssh-keygen -l -f "$file" | awk '{print $2, $NF}' | paste -sd ' ')"
        return 0
    fi
    while IFS= read -r line; do
        line=${line%$'\r'}
        line=${line#"${line%%[![:space:]]*}"}
        case $line in
            '' | '#'*) ;;
            ssh-ed25519\ * | ssh-rsa\ * | ecdsa-sha2-nistp*\ * | sk-*@openssh.com\ *)
                printf '%s %s\n' "$VPS_HOST" "$line"
                ;;
            *) printf '%s\n' "$line" ;;
        esac
    done <<<"$VPS_KNOWN_HOSTS" >"$file"
    ssh-keygen -F "$VPS_HOST" -f "$file" >/dev/null || fail "VPS_KNOWN_HOSTS holds no key for $VPS_HOST"
}

for name in VPS_HOST VPS_USER VPS_SSH_KEY BLESK_BRANCH; do
    [[ -n ${!name:-} ]] || fail "$name is required"
done

# Writes VPS_SSH_KEY as ssh expects it. A key pasted on Windows may carry CR line ends, indentation
# or, flattened into one line, spaces where its line breaks were; ssh refuses all of these.
write_key() {
    local key=${VPS_SSH_KEY//$'\r'/} begin='-----BEGIN OPENSSH PRIVATE KEY-----' end='-----END OPENSSH PRIVATE KEY-----'
    key=$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e '/^$/d' <<<"$key")
    if [[ $key == "$begin "*" $end" ]]; then
        local body=${key#"$begin "} lines
        read -ra lines <<<"${body%" $end"}"
        key=$(printf '%s\n' "$begin" "${lines[@]}" "$end")
    fi
    printf '%s\n' "$key" >"$1"
    chmod 600 "$1"
}

# Says what VPS_SSH_KEY holds instead of a private key, without printing any of it.
explain_key() {
    local first
    first=$(grep -m1 -v '^[[:space:]]*$' "$1" || true)
    case $first in
        ssh-ed25519\ * | ssh-rsa\ * | ecdsa-sha2-* | *' ssh-ed25519 '* | *' ssh-rsa '*)
            echo "VPS_SSH_KEY holds a public key (a line with ssh-ed25519 or ssh-rsa), the server's or the deploy key's:" \
                "it needs the private key, the text from BEGIN OPENSSH PRIVATE KEY to END OPENSSH PRIVATE KEY" ;;
        '' ) echo "VPS_SSH_KEY is blank" ;;
        -----BEGIN*)
            echo "VPS_SSH_KEY starts like a private key but is cut or protected by a passphrase ($(wc -l <"$1") lines)" ;;
        *) echo "VPS_SSH_KEY is not an SSH key: it holds $(wc -l <"$1") line(s) of $(wc -c <"$1") characters" ;;
    esac
}

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
write_key "$dir/key"
public_key=$(ssh-keygen -y -P '' -f "$dir/key" 2>/dev/null) || fail "$(explain_key "$dir/key")"
# The public half is not secret; kept in the run summary, it can be given to a new server on a move.
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    printf 'Deploy key to authorize on a new server:\n~~~\n%s\n~~~\n' "$public_key" >>"$GITHUB_STEP_SUMMARY"
fi
trust_host "$dir/known_hosts"

# Settings for the remote side, quoted so that any value survives the remote shell as it is. They
# go into the environment rather than onto sudo's command line, which any local user can read.
exports="export BLESK_SSH_KEYS_ONLY=1"
for name in "${FORWARDED[@]}"; do
    if [[ -n ${!name:-} ]]; then exports+=$'\n'"export $name=$(printf '%q' "${!name}")"; fi
done

if [[ $VPS_USER == root ]]; then
    raw_url="https://raw.githubusercontent.com/${GITHUB_REPOSITORY:-gotblesk/blesk}/refs/heads/$BLESK_BRANCH/scripts/deploy.sh"
    remote=$(
        cat <<EOF
script=/opt/blesk/scripts/deploy.sh
if [ ! -f "\$script" ]; then
    script=\$(mktemp)
    curl -fsSL $(printf '%q' "$raw_url") -o "\$script"
fi
exec bash "\$script"
EOF
    )
else
    remote=$(
        cat <<EOF
if [ ! -x $WRAPPER ]; then
    echo "$WRAPPER is missing: run scripts/server-bootstrap.sh on the server first" >&2
    exit 1
fi
exec sudo -n $WRAPPER
EOF
    )
fi

# The commands travel on stdin rather than as the SSH command, which the remote login shell would
# get as its arguments, so the secrets in them stay out of its command line.
ssh -i "$dir/key" -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$dir/known_hosts" \
    -o GlobalKnownHostsFile=/dev/null -o ConnectTimeout=30 -o ServerAliveInterval=30 -o ServerAliveCountMax=10 \
    "$VPS_USER@$VPS_HOST" 'bash -s' <<<"set -euo pipefail
$exports
$remote"
