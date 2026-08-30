#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 7 ]]; then
  echo "Usage: $0 <jar> <mod version> <minecraft> <java> <NeoForge range> <Minecraft range> <Jade range>" >&2
  exit 2
fi

jar_path="$1"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
expected_version="$2"
minecraft_version="$3"
java_version="$4"
neo_range="$5"
minecraft_range="$6"
jade_range="$7"
expected_name="betterbees-${expected_version}-neoforge-${minecraft_version}.jar"

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
  "versionRange=\"${neo_range}\"" \
  'modId="jade"' \
  'type="optional"' \
  "versionRange=\"${jade_range}\"" \
  "versionRange=\"${minecraft_range}\"" \
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

class_major="$(unzip -p "$jar_path" com/betterbees/BetterBees.class | od -An -t u1 -N 8 | awk '{print $7 * 256 + $8}')"
expected_major=$((java_version + 44))
if [[ "$class_major" != "$expected_major" ]]; then
  echo "Expected Java $java_version class major $expected_major, got $class_major" >&2
  exit 1
fi

echo "Verified $jar_path for Better Bees $expected_version"
