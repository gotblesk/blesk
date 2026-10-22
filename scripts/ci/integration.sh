#!/usr/bin/env bash
# The integration test of two real clients and the real server (integration/RealServerTest): starts
# the server stack of the VPS with scripts/ci/test-server.sh and runs the test against it. The stack
# stays up for the emulator smoke test; `scripts/ci/test-server.sh down` removes it.
#
#   scripts/ci/integration.sh            needs ./gradlew :server:installDist first
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
server="$root/scripts/ci/test-server.sh"

# Not `IFS== read`: it would drop the "=" a Base64 pin ends with.
config=$("$server" up)
while read -r line; do
    [[ $line == BLESK_IT_*=* ]] && export "${line%%=*}=${line#*=}"
done <<<"$config"

BLESK_IT_INVITES=$("$server" invites | head -n 1)
[[ -n $BLESK_IT_INVITES ]] || { echo "the test server has no bootstrap invites left" >&2; exit 1; }
export BLESK_IT_INVITES

status=0
"$root/gradlew" --console=plain -p "$root" :integration:testDebugUnitTest || status=$?
if ((status != 0)); then
    echo "--- server log ---" >&2
    "$server" logs | tail -n 200 >&2
fi
exit "$status"
