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


def matrix(kind):
    rows = []
    for minecraft, target in TARGETS.items():
        common = {"minecraft": minecraft, "java": str(target["java"])}
        if kind == "validation":
            for platform in ("neoforge", "fabric", "quilt"):
                if platform == "quilt" and not target.get("quiltSupported", True):
                    continue
                neo = platform == "neoforge"
                loader_key = {"neoforge": "neo", "fabric": "fabricLoader", "quilt": "quiltLoader"}[platform]
                for endpoint in ("Floor", "Latest"):
                    rows.append({
                        **common, "platform": platform, "endpoint": endpoint.lower(),
                        "project": target["project" if neo else "fabricProject"],
                        "loader": target[loader_key + endpoint],
                        "api": "" if neo else target["fabricApi" + endpoint],
                        "jade": target["jadeLatest" if neo else "fabricJadeLatest"],
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
                    "loaders": "fabric quilt" if platform == "fabric" and target.get("quiltSupported", True) else platform,
                })
        else:
            raise ValueError(f"Unknown matrix kind: {kind}")
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("validation", "neo", "neo-jade", "fabric", "quilt", "fabric-jade", "package"))
    args = parser.parse_args()
    print(json.dumps({"include": matrix(args.kind)}, separators=(",", ":")))
