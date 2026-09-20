#!/usr/bin/env python3
"""Generate GitHub Actions matrices from the committed target manifest."""

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TARGETS = json.loads((ROOT / "gradle" / "targets.json").read_text(encoding="utf-8"))


def endpoint_values(target, floor_key, latest_key):
    values = [target[floor_key], target[latest_key]]
    return list(dict.fromkeys(values))


def release_dependencies(minecraft, platform, target):
    def dependency(name, project_id, version_range, dependency_type="optional"):
        return dict(name=name, project_id=project_id, version_range=version_range,
                    dependency_type=dependency_type)

    neo = platform == "neoforge"
    dependencies = [dependency("Jade", "nvQzSEkH",
                               target["jadeRange" if neo else "fabricJadeModrinthRange"])]
    if not neo:
        dependencies.append(dependency("Fabric API", "P7dR8mSH", target["fabricApiRange"], "required"))
    # These adapters are packaged only in the 1.21.1 artifacts.
    if minecraft == "1.21.1":
        if neo:
            dependencies.append(dependency("Create", "LNytGWDc", "[6.0.10,6.1)"))
        dependencies.append(dependency("The Bumblezone", "38tpSycf" if neo else "eA8SXqWL",
                                       "[7.16.1,7.17)" if neo else "[7.16.0,7.17)"))
    return dependencies


def matrix(kind, profile="routine"):
    if profile not in ("routine", "full"):
        raise ValueError(f"Unknown validation profile: {profile}")
    rows = []
    for minecraft, target in TARGETS.items():
        common = {"minecraft": minecraft, "java": str(target["java"])}
        if kind == "validation":
            platforms = ("neoforge", "fabric") if profile == "routine" else ("neoforge", "fabric", "quilt")
            for platform in platforms:
                if platform == "quilt" and not target.get("quiltSupported", True):
                    continue
                neo = platform == "neoforge"
                loader_key = {"neoforge": "neo", "fabric": "fabricLoader", "quilt": "quiltLoader"}[platform]
                for endpoint in (("Floor",) if profile == "routine" else ("Floor", "Latest")):
                    rows.append({
                        **common, "platform": platform, "endpoint": endpoint.lower(), "profile": profile,
                        "project": target["project" if neo else "fabricProject"],
                        "loader": target[loader_key + endpoint],
                        "api": "" if neo else target["fabricApi" + endpoint],
                    })
        elif kind == "neo":
            versions = endpoint_values(target, "neoFloor", "neoLatest")
            rows += [{**common, "project": target["project"], "neo": version} for version in versions]
        elif kind == "neo-jade":
            rows += [
                {**common, "project": target["project"], "neo": target[neo_key], "jade": target[jade_key]}
                for neo_key, jade_key in (("neoFloor", "jadeFloor"), ("neoLatest", "jadeLatest"))
            ]
        elif kind in ("fabric", "quilt"):
            if kind == "quilt" and not target.get("quiltSupported", True):
                continue
            loader_key = "fabricLoader" if kind == "fabric" else "quiltLoader"
            for endpoint in ("Floor", "Latest"):
                row = {
                    **common,
                    "project": target["fabricProject"],
                    "platform": kind,
                    "loader": target[f"{loader_key}{endpoint}"],
                    "api": target[f"fabricApi{endpoint}"],
                }
                if row not in rows:
                    rows.append(row)
        elif kind == "fabric-jade":
            for platform in ("fabric", "quilt"):
                if platform == "quilt" and not target.get("quiltSupported", True):
                    continue
                loader_key = "fabricLoader" if platform == "fabric" else "quiltLoader"
                for endpoint in ("Floor", "Latest"):
                    row = {
                        **common,
                        "project": target["fabricProject"],
                        "platform": platform,
                        "loader": target[f"{loader_key}{endpoint}"],
                        "api": target[f"fabricApi{endpoint}"],
                        "jade": target[f"fabricJade{endpoint}"],
                    }
                    if row not in rows:
                        rows.append(row)
        elif kind == "package":
            for platform in ("neoforge", "fabric"):
                rows.append({
                    **common,
                    "platform": platform,
                    "jadeRange": target["jadeRange"] if platform == "neoforge" else target["fabricJadeModrinthRange"],
                    "fabricApiRange": target.get("fabricApiRange", ""),
                    "dependencies": release_dependencies(minecraft, platform, target),
                    "loaders": "fabric quilt" if platform == "fabric" and target.get("quiltSupported", True) else platform,
                })
        else:
            raise ValueError(f"Unknown matrix kind: {kind}")
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("validation", "neo", "neo-jade", "fabric", "quilt", "fabric-jade", "package"))
    parser.add_argument("--profile", choices=("routine", "full"), default="routine")
    args = parser.parse_args()
    print(json.dumps({"include": matrix(args.kind, args.profile)}, separators=(",", ":")))
