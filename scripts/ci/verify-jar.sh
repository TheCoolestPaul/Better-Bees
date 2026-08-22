#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <Better Bees jar> [expected version]" >&2
  exit 2
fi

jar_path="$1"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
expected_version="${2:-$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties")}"
expected_name="betterbees-${expected_version}.jar"

if [[ ! -f "$jar_path" ]]; then
  echo "Release jar does not exist: $jar_path" >&2
  exit 1
fi
if [[ "$(basename "$jar_path")" != "$expected_name" ]]; then
  echo "Expected jar name $expected_name, got $(basename "$jar_path")" >&2
  exit 1
fi

unzip -tq "$jar_path" >/dev/null
entries="$(unzip -Z1 "$jar_path")"
for required in \
  META-INF/neoforge.mods.toml \
  META-INF/LICENSE \
  META-INF/THIRD_PARTY_NOTICES.md \
  betterbees.mixins.json; do
  if ! grep -Fxq "$required" <<<"$entries"; then
    echo "Required jar entry is missing: $required" >&2
    exit 1
  fi
done

metadata="$(unzip -p "$jar_path" META-INF/neoforge.mods.toml)"
for expected in \
  'modId="betterbees"' \
  "version=\"${expected_version}\"" \
  'versionRange="[21.1.1,21.2)"' \
  'modId="jade"' \
  'type="optional"' \
  'versionRange="[15.1.6,16)"' \
  'versionRange="[1.21.1,1.21.2)"' \
  'config="betterbees.mixins.json"'; do
  if ! grep -Fq "$expected" <<<"$metadata"; then
    echo "Required metadata is missing: $expected" >&2
    exit 1
  fi
done

if grep -Eq '^snownee/jade/' <<<"$entries"; then
  echo "Release jar must not bundle Jade classes" >&2
  exit 1
fi

echo "Verified $jar_path for Better Bees $expected_version"
