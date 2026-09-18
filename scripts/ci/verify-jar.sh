#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 8 ]]; then
  echo "Usage: $0 <neoforge|fabric> <jar> <mod version> <minecraft> <java> <loader range> <Minecraft range> <Jade range>" >&2
  exit 2
fi

platform="$1"
jar_path="$2"
expected_version="$3"
minecraft_version="$4"
java_version="$5"
loader_range="$6"
minecraft_range="$7"
jade_range="$8"
expected_name="betterbees-${expected_version}-${platform}-${minecraft_version}.jar"

[[ -f "$jar_path" ]] || { echo "Release jar does not exist: $jar_path" >&2; exit 1; }
[[ "$(basename "$jar_path")" == "$expected_name" ]] || { echo "Expected $expected_name, got $(basename "$jar_path")" >&2; exit 1; }

unzip -tq "$jar_path" >/dev/null
entries="$(unzip -Z1 "$jar_path")"
for required in META-INF/LICENSE META-INF/THIRD_PARTY_NOTICES.md betterbees.mixins.json; do
  grep -Fxq "$required" <<<"$entries" || { echo "Required jar entry is missing: $required" >&2; exit 1; }
done

if [[ "$platform" == neoforge ]]; then
  grep -Fxq META-INF/neoforge.mods.toml <<<"$entries" || { echo 'NeoForge metadata is missing' >&2; exit 1; }
  metadata="$(unzip -p "$jar_path" META-INF/neoforge.mods.toml)"
  for expected in 'modId="betterbees"' "version=\"${expected_version}\"" "versionRange=\"${loader_range}\"" \
    'modId="jade"' 'type="optional"' "versionRange=\"${jade_range}\"" "versionRange=\"${minecraft_range}\"" \
    'config="betterbees.mixins.json"'; do
    grep -Fq "$expected" <<<"$metadata" || { echo "Required metadata is missing: $expected" >&2; exit 1; }
  done
elif [[ "$platform" == fabric ]]; then
  grep -Fxq fabric.mod.json <<<"$entries" || { echo 'Fabric metadata is missing' >&2; exit 1; }
  unzip -p "$jar_path" fabric.mod.json | python3 -c '
import json,sys
m=json.load(sys.stdin)
version,mc,java,loader,jade=sys.argv[1:]
assert m["id"]=="betterbees" and m["version"]==version
assert m["depends"]["minecraft"]==mc
assert m["depends"]["java"]==f">={java}"
assert m["depends"]["fabricloader"]==loader
assert m["depends"]["fabric-api"]=="*"
assert m["suggests"]["jade"]==jade
assert "com.betterbees.compat.jade.BetterBeesJadePlugin" in m["entrypoints"]["jade"]
assert "betterbees.mixins.json" in m["mixins"]
' "$expected_version" "$minecraft_version" "$java_version" "$loader_range" "$jade_range"
  if grep -Eq '(^|/)neoforge\.mods\.toml$|^net/neoforged/' <<<"$entries"; then
    echo 'Fabric jar must not contain NeoForge metadata or classes' >&2; exit 1
  fi
else
  echo "Unknown platform: $platform" >&2; exit 2
fi

if grep -Eq '^snownee/jade/' <<<"$entries"; then echo 'Release jar must not bundle Jade classes' >&2; exit 1; fi
if grep -Eq '^com/simibubi/create/|^net/createmod/|^dev/engine_room/flywheel/' <<<"$entries"; then
  echo 'Release jar must not bundle Create or its dependencies' >&2; exit 1
fi
if [[ "$platform" == neoforge && "$minecraft_version" == 1.21.1 ]]; then
  grep -Fxq betterbees.create.mixins.json <<<"$entries" || { echo 'Create mixin config is missing' >&2; exit 1; }
  grep -Fxq com/betterbees/compat/create/CreateMixinPlugin.class <<<"$entries" || { echo 'Create loading guard is missing' >&2; exit 1; }
  grep -Fq 'config="betterbees.create.mixins.json"' <<<"$metadata" || { echo 'Create mixin metadata is missing' >&2; exit 1; }
elif grep -Eq '^com/betterbees/compat/create/|^betterbees.create.mixins.json$' <<<"$entries"; then
  echo 'Create adapters must only be packaged for NeoForge 1.21.1' >&2; exit 1
fi
if grep -Eq '^com/telepathicgrunt/|^com/teamresourceful/' <<<"$entries"; then
  echo 'Release jar must not bundle Bumblezone or Resourceful Lib' >&2; exit 1
fi
if grep -Eq '^com/betterbees/compat/(bumblezone|create)/.*GameTests' <<<"$entries"; then
  echo 'Release jar must not contain optional integration tests' >&2; exit 1
fi
if [[ "$minecraft_version" == 1.21.1 ]]; then
  grep -Fxq betterbees.bumblezone.mixins.json <<<"$entries" || { echo 'Bumblezone mixin config missing' >&2; exit 1; }
  grep -Fxq com/betterbees/compat/bumblezone/BumblezoneMixinPlugin.class <<<"$entries" || { echo 'Bumblezone loading guard missing' >&2; exit 1; }
elif grep -Eq '^com/betterbees/compat/bumblezone/|^betterbees.bumblezone.mixins.json$' <<<"$entries"; then
  echo 'Bumblezone adapters must only be packaged for 1.21.1' >&2; exit 1
fi
class_major="$(unzip -p "$jar_path" com/betterbees/BetterBees.class | od -An -t u1 -N 8 | awk '{print $7 * 256 + $8}')"
expected_major=$((java_version + 44))
[[ "$class_major" == "$expected_major" ]] || { echo "Expected Java $java_version class major $expected_major, got $class_major" >&2; exit 1; }
echo "Verified $jar_path for Better Bees $expected_version ($platform)"
