#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <server|client> <Gradle project> <NeoForge version> [additional Gradle arguments...]" >&2
  exit 2
fi

mode="$1"
project="$2"
neo_version="$3"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-240}"

case "$mode" in
  server)
    gradle_task=":${project}:runServer"
    markers=('Done \([0-9.]+s\)! For help')
    ;;
  client)
    gradle_task=":${project}:runClient"
    markers=(
      'Reloading ResourceManager:.*mod/betterbees'
      'textures/atlas/blocks.png-atlas'
      'textures/atlas/gui.png-atlas'
    )
    ;;
  *)
    echo "Unknown smoke mode: $mode" >&2
    exit 2
    ;;
esac

for argument in "${@:4}"; do
  if [[ "$argument" == "-PwithJade=true" ]]; then
    markers+=('Loaded plugin from com\.betterbees\.compat\.jade\.BetterBeesJadePlugin')
    break
  fi
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p build/smoke run
log_file="build/smoke/${mode}-${project}-${neo_version}.log"
if [[ "$mode" == "server" ]]; then
  minecraft_version="${project#mc}"
  minecraft_version="${minecraft_version//_/.}"
  mkdir -p "run/${minecraft_version}/server"
  printf 'eula=true\n' > "run/${minecraft_version}/server/eula.txt"
fi

command=(./gradlew --no-daemon "-Pneo_version=${neo_version}" "${@:4}" "$gradle_task")
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
      kill -0 "$smoke_pid" 2>/dev/null || return
      sleep 1
    done
    kill -KILL -- "-$smoke_pid" 2>/dev/null || kill -KILL "$smoke_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "Starting Better Bees $mode smoke test for $project on NeoForge $neo_version"
setsid "${command[@]}" >"$log_file" 2>&1 &
smoke_pid=$!
deadline=$((SECONDS + timeout_seconds))

fatal_pattern='Mixin apply failed|MixinApplyError|InvalidMixinException|InjectionError|Exception in thread "main"|MOD LOADING ERROR|Failed to create mod instance|Duplicate UID|Error loading plugin at com\.betterbees\.compat\.jade|Caught unhandled exception'

while (( SECONDS < deadline )); do
  if grep -Eiq "$fatal_pattern" "$log_file"; then
    echo "Fatal launch error detected in $log_file" >&2
    tail -n 200 "$log_file" >&2
    exit 1
  fi

  healthy=true
  for marker in "${markers[@]}"; do
    if ! grep -Eq "$marker" "$log_file"; then
      healthy=false
      break
    fi
  done
  if [[ "$healthy" == true ]]; then
    echo "Better Bees $mode reached a healthy initialized state on NeoForge $neo_version"
    exit 0
  fi

  if ! kill -0 "$smoke_pid" 2>/dev/null; then
    wait "$smoke_pid" || status=$?
    echo "Smoke process exited before initialization (status ${status:-0})" >&2
    tail -n 200 "$log_file" >&2
    exit 1
  fi
  sleep 2
done

echo "Timed out after ${timeout_seconds}s waiting for $mode initialization" >&2
tail -n 200 "$log_file" >&2
exit 1
