#!/usr/bin/env bash
# Retry transient dependency downloads only before any Gradle task has started.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
attempt_log="$(mktemp)"
trap 'rm -f "$attempt_log"' EXIT

for attempt in 1 2 3; do
  status=0
  ./gradlew --console=plain "$@" 2>&1 | tee "$attempt_log" || status=$?
  [[ "$status" != 0 ]] || exit 0
  # In particular, never replay a world upgrade or GameTest that already ran.
  # Permanent HTTP errors (400/401/403/404) and other configuration errors fail.
  if [[ "$attempt" == 3 ]] \
      || grep -Eq '^> Task ' "$attempt_log" \
      || ! grep -Fq 'A problem occurred configuring ' "$attempt_log" \
      || ! grep -Eq "Could not (GET|HEAD) .*Received status code (429|500|502|503|504) from server" "$attempt_log"; then
    exit "$status"
  fi
  delay=$((15 * attempt + RANDOM % 6))
  echo "Transient dependency download failure before task execution; retrying in ${delay}s (attempt $((attempt + 1))/3)" >&2
  sleep "$delay"
done
