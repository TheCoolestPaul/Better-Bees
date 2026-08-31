#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# Read values as array elements, never evaluate JSON as shell code.
mapfile -t target < <(python3 - <<'PY'
import json, os
t = json.loads(os.environ['TARGET'])
for key in ('platform', 'project', 'loader', 'api', 'jade', 'endpoint'):
    print(t[key])
PY
)
platform="${target[0]}"; project="${target[1]}"; loader="${target[2]}"
api="${target[3]}"; jade="${target[4]}"; endpoint="${target[5]}"
case "$platform" in
  neoforge) args=("-Pneo_version=$loader"); task=runGameTestServer; smoke_args=() ;;
  fabric|quilt)
    args=("-Pfabric_target=$project" "-Pfabric_api_version=$api")
    if [[ "$platform" == quilt ]]; then args+=(-PwithQuilt=true "-Pquilt_loader_version=$loader");
    else args+=("-Pfabric_loader_version=$loader"); fi
    task=runGameTest
    smoke_args=("-Pfabric_api_version=$api")
    ;;
  *) echo "Unknown platform: $platform" >&2; exit 2 ;;
esac
mkdir -p build/smoke
test_log="build/smoke/gametest-${platform}-${project}-${endpoint}.log"
./gradlew --no-daemon "${args[@]}" performancePolicyTest ":$project:build" ":$project:$task" 2>&1 | tee "$test_log"
# Some loader bootstrap failures exit zero. Require actual suite completion,
# including at least all of our shared tests, before accepting the Gradle result.
python3 - "$test_log" <<'PY'
import pathlib, re, sys
source = pathlib.Path('src/main/java/com/betterbees/gametest/BetterBeesGameTests.java').read_text()
expected = len(re.findall(r'@GameTest\(', source))
log = pathlib.Path(sys.argv[1]).read_text(errors='replace')
passed = [int(n) for n in re.findall(r'All (\d+) required tests passed', log)]
if not expected or max(passed, default=0) < expected:
    sys.exit(f'Expected at least {expected} passing GameTests; no complete suite was reported')
PY
bash scripts/ci/smoke-launch.sh client "$platform" "$project" "$loader" "${smoke_args[@]}"
if [[ "$endpoint" == latest ]]; then
  for mode in server client; do
    bash scripts/ci/smoke-launch.sh "$mode" "$platform" "$project" "$loader" "${smoke_args[@]}" -PwithJade=true "-Pjade_version=$jade"
  done
fi
