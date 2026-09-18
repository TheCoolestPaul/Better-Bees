#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# Read values as array elements, never evaluate JSON as shell code.
target_values="$(python3 - <<'PY'
import json, os, sys
sys.stdout.reconfigure(newline='\n')
t = json.loads(os.environ['TARGET'])
profile = t.get('profile', 'routine')
if profile not in ('routine', 'full'):
    raise SystemExit(f'Unknown validation profile: {profile}')
if profile == 'routine' and (t['platform'] == 'quilt' or t['endpoint'] != 'floor'):
    raise SystemExit('Routine validation requires NeoForge/Fabric floor targets')
for key in ('platform', 'project', 'loader', 'api', 'endpoint'):
    print(t[key])
print(profile)
PY
)"
mapfile -t target <<< "$target_values"
platform="${target[0]}"; project="${target[1]}"; loader="${target[2]}"
api="${target[3]}"; endpoint="${target[4]}"
profile="${target[5]}"
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
if [[ "$profile" == routine && "$platform" == neoforge ]]; then
  args+=(--init-script scripts/ci/server-only.gradle)
fi
mkdir -p build/smoke
test_log="build/smoke/gametest-${platform}-${project}-${endpoint}.log"
tasks=()
if [[ "$platform" != quilt ]]; then tasks+=(":$project:build"); fi
tasks+=(":$project:$task")
./gradlew --no-daemon "${args[@]}" "${tasks[@]}" 2>&1 | tee "$test_log"
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
if [[ "$profile" == full ]]; then
  bash scripts/ci/smoke-launch.sh client "$platform" "$project" "$loader" "${smoke_args[@]}"
fi
