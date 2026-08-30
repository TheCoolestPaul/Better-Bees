#!/usr/bin/env python3
"""Validate Better Bees' Modrinth target, artifact, and optional dependencies."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


API_ROOT = "https://api.modrinth.com/v2"
PROJECT_ID_PATTERN = re.compile(r"^[A-Za-z0-9]{8}$")
VERSION_RANGE_PATTERN = re.compile(
    r"^\[(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*),"
    r"(0|[1-9][0-9]*)\)$"
)
VERSION_PREFIX_PATTERN = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
)
DEPENDENCY_TYPES = {"required", "optional"}


class ValidationError(RuntimeError):
    pass


def load_manifest(path: Path, expected_jade_range: str) -> list[dict[str, str]]:
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValidationError(f"Cannot read dependency manifest {path}: {exc}") from exc

    if set(manifest) != {"dependencies"}:
        raise ValidationError("Dependency manifest must contain only dependencies")
    dependencies = manifest["dependencies"]
    if not isinstance(dependencies, list) or not dependencies:
        raise ValidationError("Dependency manifest must contain at least one dependency")

    required_keys = {"name", "project_id", "dependency_type", "version_range"}
    seen_ids: set[str] = set()
    jade_entries = 0
    for index, dependency in enumerate(dependencies):
        if not isinstance(dependency, dict) or set(dependency) != required_keys:
            raise ValidationError(
                f"Dependency {index + 1} must contain exactly {sorted(required_keys)}"
            )
        if not all(isinstance(dependency[key], str) and dependency[key] for key in required_keys):
            raise ValidationError(f"Dependency {index + 1} contains an empty or non-string value")
        project_id = dependency["project_id"]
        if not PROJECT_ID_PATTERN.fullmatch(project_id):
            raise ValidationError(f"Invalid Modrinth project ID: {project_id}")
        if project_id in seen_ids:
            raise ValidationError(f"Duplicate Modrinth dependency: {project_id}")
        if dependency["dependency_type"] not in DEPENDENCY_TYPES:
            raise ValidationError(
                f"Unsupported dependency type for {project_id}: {dependency['dependency_type']}"
            )
        if not VERSION_RANGE_PATTERN.fullmatch(dependency["version_range"]):
            raise ValidationError(
                f"Unsupported dependency version range for {project_id}: {dependency['version_range']}"
            )
        seen_ids.add(project_id)
        if project_id == "nvQzSEkH":
            jade_entries += 1
            if dependency["name"] != "Jade" or dependency["dependency_type"] != "optional":
                raise ValidationError("Jade must be declared exactly once as an optional dependency")
            if dependency["version_range"] != expected_jade_range:
                raise ValidationError(
                    "Jade dependency range differs from gradle.properties: "
                    f"expected {expected_jade_range}, got {dependency['version_range']}"
                )
    if jade_entries != 1:
        raise ValidationError("Jade (nvQzSEkH) must be declared exactly once")
    return dependencies


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def api_get(path: str, token: str) -> Any:
    request = urllib.request.Request(
        f"{API_ROOT}{path}",
        headers={
            "Authorization": token,
            "User-Agent": "BetterBees-release-workflow/1.0 "
            "(github.com/TheCoolestPaul/Better-Bees)",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise ValidationError(f"Modrinth API returned HTTP {exc.code} for {path}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise ValidationError(f"Cannot reach Modrinth API for {path}: {exc.reason}") from exc


def compatible_versions(project_id: str, loader: str, game_version: str, token: str) -> list[Any]:
    query = urllib.parse.urlencode(
        {"loaders": json.dumps([loader]), "game_versions": json.dumps([game_version])}
    )
    return api_get(f"/project/{project_id}/version?{query}", token)


def version_in_range(version_number: str, version_range: str) -> bool:
    version_match = VERSION_PREFIX_PATTERN.match(version_number)
    range_match = VERSION_RANGE_PATTERN.fullmatch(version_range)
    if not version_match or not range_match:
        return False
    version = tuple(int(version_match.group(index)) for index in range(1, 4))
    minimum = tuple(int(range_match.group(index)) for index in range(1, 4))
    maximum_major = int(range_match.group(4))
    return version >= minimum and version < (maximum_major, 0, 0)


def dependency_pairs(dependencies: list[dict[str, str]]) -> set[tuple[str, str]]:
    return {(entry["project_id"], entry["dependency_type"]) for entry in dependencies}


def validate_existing_version(
    version: dict[str, Any],
    display_name: str,
    artifact: Path,
    version_type: str,
    loaders: list[str],
    game_version: str,
    dependencies: list[dict[str, str]],
) -> None:
    if isinstance(loaders, str):
        loaders = [loaders]
    if version.get("name") != display_name:
        raise ValidationError(f"Existing Modrinth version has unexpected name: {version.get('name')!r}")
    if version.get("version_type") != version_type:
        raise ValidationError(
            f"Existing Modrinth version type is {version.get('version_type')!r}, expected {version_type!r}"
        )
    for loader in loaders:
        if loader not in version.get("loaders", []):
            raise ValidationError(f"Existing Modrinth version does not include loader {loader}")
    if game_version not in version.get("game_versions", []):
        raise ValidationError(f"Existing Modrinth version does not include Minecraft {game_version}")

    primary_files = [entry for entry in version.get("files", []) if entry.get("primary")]
    if len(primary_files) != 1:
        raise ValidationError("Existing Modrinth version must have exactly one primary file")
    primary_file = primary_files[0]
    if primary_file.get("filename") != artifact.name:
        raise ValidationError(
            f"Existing Modrinth filename {primary_file.get('filename')!r} "
            f"does not match {artifact.name!r}"
        )
    if primary_file.get("hashes", {}).get("sha512", "").lower() != sha512(artifact):
        raise ValidationError("Existing Modrinth jar differs from the validated release jar")

    actual_dependencies: set[tuple[str, str]] = set()
    for dependency in version.get("dependencies", []):
        if dependency.get("version_id") is not None:
            raise ValidationError("Existing Modrinth release contains an exact-version dependency pin")
        project_id = dependency.get("project_id")
        dependency_type = dependency.get("dependency_type")
        if project_id and dependency_type:
            actual_dependencies.add((project_id, dependency_type))
    expected_dependencies = dependency_pairs(dependencies)
    if actual_dependencies != expected_dependencies:
        raise ValidationError(
            "Existing Modrinth dependencies differ from the release manifest: "
            f"expected {sorted(expected_dependencies)}, got {sorted(actual_dependencies)}"
        )


def write_outputs(publish: bool, dependencies: list[dict[str, str]]) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        return
    dependency_lines = "\n".join(
        f"{entry['project_id']}({entry['dependency_type']})" for entry in dependencies
    )
    with open(output_path, "a", encoding="utf-8") as output:
        output.write(f"publish={'true' if publish else 'false'}\n")
        output.write("dependencies<<BETTERBEES_DEPENDENCIES\n")
        output.write(f"{dependency_lines}\n")
        output.write("BETTERBEES_DEPENDENCIES\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--jade-version-range", required=True)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--target-project")
    parser.add_argument("--loader", action="append", dest="loaders")
    parser.add_argument("--game-version", required=True)
    parser.add_argument("--version-type", choices=("release", "beta", "alpha"), required=True)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--display-name")
    parser.add_argument("--online", action="store_true")
    parser.add_argument("--expect-existing", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        dependencies = load_manifest(args.manifest, args.jade_version_range)
        loaders = args.loaders or ["neoforge"]
        if not args.online:
            print(f"Validated {len(dependencies)} Modrinth dependency entry")
            return 0

        if not args.target_project or not PROJECT_ID_PATTERN.fullmatch(args.target_project):
            raise ValidationError(
                "MODRINTH_PROJECT_ID must be configured as the target project's eight-character ID"
            )
        if args.artifact is None or args.display_name is None:
            raise ValidationError("--artifact and --display-name are required with --online")
        if not args.artifact.is_file():
            raise ValidationError(f"Release artifact does not exist: {args.artifact}")
        token = os.environ.get("MODRINTH_TOKEN", "")
        if not token:
            raise ValidationError("MODRINTH_TOKEN is required for online validation")

        target = api_get(f"/project/{args.target_project}", token)
        if target.get("id") != args.target_project:
            raise ValidationError(f"Target resolved to the wrong Modrinth project: {target.get('id')!r}")
        print(f"Validated target project {target.get('title')} ({target.get('status')})")

        for dependency in dependencies:
            project = api_get(f"/project/{dependency['project_id']}", token)
            if project.get("id") != dependency["project_id"]:
                raise ValidationError(f"Dependency ID mismatch for {dependency['name']}")
            # Fabric artifacts are intentionally also marked Quilt-compatible;
            # their dependencies are published as Fabric artifacts and run unchanged on Quilt.
            dependency_loader = "fabric" if "fabric" in loaders else loaders[0]
            versions = compatible_versions(dependency["project_id"], dependency_loader, args.game_version, token)
            if not any(version_in_range(version.get("version_number", ""), dependency["version_range"]) for version in versions):
                raise ValidationError(
                    f"{dependency['name']} has no {dependency_loader} {args.game_version} version in "
                    f"{dependency['version_range']}"
                )
            print(
                f"Validated {dependency['dependency_type']} dependency {dependency['name']} "
                f"{dependency['version_range']}"
            )

        versions = api_get(f"/project/{args.target_project}/version", token)
        matching = [
            version for version in versions if version.get("version_number") == args.expected_version
        ]
        if len(matching) > 1:
            raise ValidationError(f"Multiple Modrinth versions use number {args.expected_version}")
        if matching:
            validate_existing_version(
                matching[0],
                args.display_name,
                args.artifact,
                args.version_type,
                loaders,
                args.game_version,
                dependencies,
            )
            print(f"Modrinth version {args.expected_version} already matches this release")
            write_outputs(False, dependencies)
        else:
            if args.expect_existing:
                raise ValidationError(f"Modrinth version {args.expected_version} was not created")
            print(f"Modrinth version {args.expected_version} is ready to publish")
            write_outputs(True, dependencies)
    except ValidationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
