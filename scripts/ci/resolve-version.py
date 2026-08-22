#!/usr/bin/env python3
"""Resolve and validate a Better Bees release version."""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path


SEMVER = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
    r"(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?$"
)


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int
    prerelease: tuple[str, ...] = ()

    @classmethod
    def parse(cls, value: str) -> "Version":
        match = SEMVER.fullmatch(value)
        if not match:
            raise ValueError(
                f"Invalid version '{value}'; expected strict MAJOR.MINOR.PATCH "
                "with an optional prerelease suffix (no leading v or build metadata)"
            )
        suffix = tuple(match.group(4).split(".")) if match.group(4) else ()
        return cls(int(match.group(1)), int(match.group(2)), int(match.group(3)), suffix)

    def __str__(self) -> str:
        core = f"{self.major}.{self.minor}.{self.patch}"
        return core if not self.prerelease else f"{core}-{'.'.join(self.prerelease)}"

    def compare(self, other: "Version") -> int:
        own_core = (self.major, self.minor, self.patch)
        other_core = (other.major, other.minor, other.patch)
        if own_core != other_core:
            return (own_core > other_core) - (own_core < other_core)
        if not self.prerelease or not other.prerelease:
            return (not self.prerelease) - (not other.prerelease)
        for own, theirs in zip(self.prerelease, other.prerelease):
            if own == theirs:
                continue
            own_numeric = own.isdigit()
            their_numeric = theirs.isdigit()
            if own_numeric and their_numeric:
                return (int(own) > int(theirs)) - (int(own) < int(theirs))
            if own_numeric != their_numeric:
                return -1 if own_numeric else 1
            return (own > theirs) - (own < theirs)
        return (len(self.prerelease) > len(other.prerelease)) - (
            len(self.prerelease) < len(other.prerelease)
        )

    def bump(self, mode: str) -> "Version":
        if mode == "patch":
            return Version(self.major, self.minor, self.patch + 1)
        if mode == "minor":
            return Version(self.major, self.minor + 1, 0)
        if mode == "major":
            return Version(self.major + 1, 0, 0)
        raise ValueError(f"Unsupported bump mode: {mode}")


def read_project_version(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.partition("=")[2].strip()
    raise ValueError(f"Unable to read mod_version from {path}")


def resolve(current_text: str, released_text: str, mode: str, custom: str) -> Version:
    current = Version.parse(current_text)
    released = None if released_text == "none" else Version.parse(released_text)
    baseline = current if released is None or current.compare(released) >= 0 else released

    if mode == "current":
        target = current
    elif mode == "custom":
        if not custom:
            raise ValueError("custom_version is required when release_mode is custom")
        target = Version.parse(custom)
    else:
        target = baseline.bump(mode)

    if released is not None and target.compare(released) < 0:
        raise ValueError(
            f"Release version {target} cannot be older than the last published release {released}"
        )
    if target.compare(current) < 0:
        raise ValueError(
            f"Release version {target} cannot be older than project version {current}"
        )
    return target


def write_outputs(path: Path, values: dict[str, str]) -> None:
    with path.open("a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def modrinth_version_type(version: Version) -> str:
    return "beta" if version.prerelease else "release"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    parser.add_argument("--last-release", default="none")
    parser.add_argument(
        "--mode", choices=("current", "patch", "minor", "major", "custom"), required=True
    )
    parser.add_argument("--custom-version", default="")
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    try:
        current = read_project_version(args.properties)
        target = resolve(current, args.last_release, args.mode, args.custom_version)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1

    version = str(target)
    values = {
        "current_version": current,
        "last_release": args.last_release,
        "release_version": version,
        "tag": f"v{version}",
        "jar_name": f"betterbees-{version}.jar",
        "is_prerelease": str(bool(target.prerelease)).lower(),
        "modrinth_version_type": modrinth_version_type(target),
    }
    output_path = args.github_output
    if output_path is None and os.environ.get("GITHUB_OUTPUT"):
        output_path = Path(os.environ["GITHUB_OUTPUT"])
    if output_path:
        write_outputs(output_path, values)
    for key, value in values.items():
        print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
