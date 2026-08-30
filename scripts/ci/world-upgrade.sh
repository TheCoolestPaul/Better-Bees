#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

upgrade_dir="${1:-$repo_root/build/world-upgrade}"
mkdir -p "$upgrade_dir"

targets=(mc1_21_1 mc1_21_4 mc1_21_8 mc1_21_11 mc26_1_2 mc26_2)
for target in "${targets[@]}"; do
  echo "Upgrading shared GameTest world through $target"
  ./gradlew --no-daemon "-PupgradeGameDirectory=$upgrade_dir" ":$target:runGameTestServer"
done

echo "Sequential Better Bees world upgrade completed through Minecraft 26.2"
