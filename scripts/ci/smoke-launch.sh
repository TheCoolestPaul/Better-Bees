#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <server|client> <neoforge|fabric|quilt> <Gradle project> <loader version> [additional Gradle arguments...]" >&2
  exit 2
fi

mode="$1"
platform="$2"
project="$3"
loader_version="$4"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-240}"

case "$mode" in
  server) gradle_task=":${project}:runServer" ;;
  client) gradle_task=":${project}:runClient" ;;
  *) echo "Unknown smoke mode: $mode" >&2; exit 2 ;;
esac
jade=false
for argument in "${@:5}"; do
  [[ "$argument" != "-PwithJade=true" ]] || jade=true
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p build/smoke run
log_file="build/smoke/${mode}-${platform}-${project}-${loader_version}-jade-${jade}.log"
run_dir="$repo_root/build/smoke/runs/${mode}-${platform}-${project}-${loader_version}-jade-${jade}"
mkdir -p "$run_dir"
if [[ "$mode" == "server" ]]; then
  printf 'eula=true\n' > "$run_dir/eula.txt"
fi

case "$platform" in
  neoforge) loader_args=("-Pneo_version=${loader_version}") ;;
  fabric) loader_args=("-Pfabric_target=${project}" "-Pfabric_loader_version=${loader_version}") ;;
  quilt) loader_args=("-Pfabric_target=${project}" -PwithQuilt=true "-Pquilt_loader_version=${loader_version}") ;;
  *) echo "Unknown platform: $platform" >&2; exit 2 ;;
esac
loader_args+=("-PsmokeGameDirectory=$run_dir")
# Resolve assets before starting the bounded launch. Retry only this task, once,
# and only when Gradle identifies an asset task failure (not compilation/configuration).
if [[ "$mode" == client ]]; then
  asset_log="${log_file%.log}-assets.log"
  for attempt in 1 2; do
    attempt_log="${asset_log%.log}-${attempt}.log"
    if ./gradlew --no-daemon "${loader_args[@]}" "${@:5}" ":${project}:downloadAssets" >"$attempt_log" 2>&1; then
      break
    fi
    cat "$attempt_log" >&2
    if [[ "$attempt" == 2 ]] || ! grep -Eq "Execution failed for task .*:downloadAssets'" "$attempt_log"; then
      exit 1
    fi
    echo 'Asset download failed; retrying asset preparation once' >&2
  done
fi
command=(./gradlew --no-daemon "${loader_args[@]}" "${@:5}" "$gradle_task")
if [[ "$mode" == "client" ]]; then
  # Hosted runners have no physical audio device. OpenAL's null backend keeps
  # that environmental limitation separate from client/mod initialization.
  command=(xvfb-run -a env LIBGL_ALWAYS_SOFTWARE=1 MESA_LOADER_DRIVER_OVERRIDE=llvmpipe ALSOFT_DRIVERS=null "${command[@]}")
fi

smoke_pid=''
cleanup() {
  if [[ -n "$smoke_pid" ]] && kill -0 "$smoke_pid" 2>/dev/null; then
    kill -- "-$smoke_pid" 2>/dev/null || kill "$smoke_pid" 2>/dev/null || true
    for _ in {1..10}; do
      kill -0 "$smoke_pid" 2>/dev/null || return 0
      sleep 1
    done
    kill -KILL -- "-$smoke_pid" 2>/dev/null || kill -KILL "$smoke_pid" 2>/dev/null || true
  fi
  return 0
}
trap cleanup EXIT INT TERM

echo "Starting Better Bees $mode smoke test for $project on $platform $loader_version"
: >"$log_file"
setsid "${command[@]}" >"$log_file" 2>&1 &
smoke_pid=$!
deadline=$((SECONDS + timeout_seconds))

check_args=("$log_file" "$mode" "$platform")
[[ "$jade" == false ]] || check_args+=(--jade)
healthy_since=-1
diagnostic='Waiting for launch output'
while (( SECONDS < deadline )); do
  status=0
  diagnostic="$(python3 scripts/ci/check-smoke.py "${check_args[@]}")" || status=$?
  if [[ "$status" != 0 && "$status" != 1 ]]; then
    echo "$diagnostic" >&2
    tail -n 200 "$log_file" >&2
    exit 1
  fi
  if [[ "$status" == 0 ]]; then
    # Allow deferred initialization errors to surface before accepting startup.
    if (( healthy_since < 0 )); then healthy_since=$SECONDS; fi
    if (( SECONDS - healthy_since >= 5 )) && kill -0 "$smoke_pid" 2>/dev/null; then
      echo "Better Bees $mode reached a healthy initialized state on $platform $loader_version"
      exit 0
    fi
  else
    healthy_since=-1
  fi

  if ! kill -0 "$smoke_pid" 2>/dev/null; then
    wait "$smoke_pid" || status=$?
    echo "Smoke process exited before initialization (status ${status:-0})" >&2
    tail -n 200 "$log_file" >&2
    exit 1
  fi
  sleep 2
done

echo "Timed out after ${timeout_seconds}s waiting for $mode initialization: $diagnostic" >&2
tail -n 200 "$log_file" >&2
exit 1
