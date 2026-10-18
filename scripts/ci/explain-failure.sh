#!/usr/bin/env bash
# Turns a failed Gradle run into GitHub annotations: the "What went wrong" block of the log and every
# failed test with the first line of its message. Annotations can be read through the public checks
# API, while the job log needs a signed-in account.
#
#   scripts/ci/explain-failure.sh "$RUNNER_TEMP/gradle.log"
set -uo pipefail

log=${1:?usage: explain-failure.sh <gradle log>}
limit=20
count=0

# Workflow commands end at a newline and treat % specially.
escape() {
    local text=${1//'%'/'%25'}
    text=${text//$'\r'/'%0D'}
    printf '%s' "${text//$'\n'/'%0A'}"
}

annotate() {
    ((count < limit)) || return 0
    count=$((count + 1))
    printf '::error title=%s::%s\n' "$(escape "$1")" "$(escape "$2")"
}

if [[ -f $log ]]; then
    while IFS= read -r block; do
        annotate "Gradle" "$block"
    done < <(awk '
        /^\* What went wrong:/ { taking = 1; text = ""; next }
        taking && /^\* (Try|Exception is|Get more help):/ { print text; taking = 0; next }
        taking && NF { line = $0; sub(/^[ \t]+/, "", line); text = text (text == "" ? "" : " | ") line }
        END { if (taking) print text }
    ' "$log" | cut -c1-900)
fi

while IFS= read -r report; do
    while IFS=$'\t' read -r name message; do
        annotate "Test failed" "$name: $message"
    done < <(awk '
        /<testcase / {
            match($0, /classname="[^"]*"/); cls = substr($0, RSTART + 11, RLENGTH - 12)
            match($0, /name="[^"]*"/); test = substr($0, RSTART + 6, RLENGTH - 7)
        }
        /<failure / {
            message = ""
            if (match($0, /message="[^"]*"/)) message = substr($0, RSTART + 9, RLENGTH - 10)
            gsub(/&#10;|&#13;/, " ", message); gsub(/&lt;/, "<", message); gsub(/&gt;/, ">", message)
            gsub(/&quot;/, "\"", message); gsub(/&apos;/, "\047", message); gsub(/&amp;/, "\\&", message)
            printf "%s.%s\t%s\n", cls, test, substr(message, 1, 300)
        }
    ' "$report")
done < <(find . -path '*/build/test-results/*' -name '*.xml' -not -path './legacy/*' 2>/dev/null)

((count > 0)) || annotate "Build failed" "no Gradle error block or failed test found; see the job log"
exit 0
