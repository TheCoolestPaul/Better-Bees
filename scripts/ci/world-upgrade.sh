#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

platform="${1:-neoforge}"
upgrade_dir="${2:-$repo_root/build/world-upgrade-$platform}"
mkdir -p "$upgrade_dir"

case "$platform" in
  neoforge) targets=(mc1_21_1 mc1_21_4 mc1_21_8 mc1_21_11 mc26_1_2 mc26_2); task=runGameTestServer; extra=() ;;
  fabric) targets=(fabricMc1_21_1 fabricMc1_21_4 fabricMc1_21_8 fabricMc1_21_11 fabricMc26_1_2 fabricMc26_2); task=runGameTest; extra=() ;;
  quilt) targets=(fabricMc1_21_1 fabricMc1_21_4 fabricMc1_21_8 fabricMc1_21_11); task=runGameTest; extra=(-PwithQuilt=true) ;;
  *) echo "Unknown platform: $platform" >&2; exit 2 ;;
esac
for target in "${targets[@]}"; do
  echo "Upgrading $platform GameTest world through $target"
  args=(--no-daemon "-PupgradeGameDirectory=$upgrade_dir")
  if [[ "$platform" != neoforge ]]; then args+=("-Pfabric_target=$target"); fi
  ./gradlew "${args[@]}" "${extra[@]}" ":$target:$task"
done

echo "Sequential Better Bees $platform world upgrade completed through Minecraft 26.2"
