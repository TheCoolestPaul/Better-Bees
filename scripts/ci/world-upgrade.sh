#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

platform="${1:-neoforge}"
upgrade_dir="${2:-$repo_root/build/world-upgrade-$platform}"
mkdir -p "$upgrade_dir"

case "$platform" in
  neoforge) extra=() ;;
  fabric) extra=() ;;
  quilt) extra=(-PwithQuilt=true) ;;
  *) echo "Unknown platform: $platform" >&2; exit 2 ;;
esac
mapfile -t targets < <(python3 - "$platform" <<'PY'
import json, sys
for target in json.load(open('gradle/targets.json')).values():
    if sys.argv[1] == 'quilt' and not target.get('quiltSupported', True):
        continue
    print(target['project' if sys.argv[1] == 'neoforge' else 'fabricProject'])
PY
)
[[ ! -e "$upgrade_dir/world/level.dat" ]] || { echo 'Upgrade validation requires a fresh directory; refusing to downgrade an existing world' >&2; exit 1; }
printf 'eula=true\n' > "$upgrade_dir/eula.txt"
printf 'level-name=world\nlevel-type=minecraft:flat\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n' > "$upgrade_dir/server.properties"
require_existing=false
for target in "${targets[@]}"; do
  echo "Upgrading $platform saved world through $target"
  args=(--no-daemon "-PsmokeGameDirectory=$upgrade_dir" -PupgradeValidation=true "-PupgradeRequireExisting=$require_existing")
  if [[ "$platform" != neoforge ]]; then args+=("-Pfabric_target=$target"); fi
  log_file="$upgrade_dir/upgrade-$target.log"
  ./gradlew "${args[@]}" "${extra[@]}" ":$target:runServer" 2>&1 | tee "$log_file"
  grep -Fq 'Better Bees upgrade fixture verified' "$log_file" || { echo "Upgrade fixture verification failed for $target" >&2; exit 1; }
  require_existing=true
done

echo "Sequential Better Bees $platform world upgrade completed through ${targets[-1]}"
