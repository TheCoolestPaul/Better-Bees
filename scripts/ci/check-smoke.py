#!/usr/bin/env python3
"""Classify startup logs: 0 healthy, 1 incomplete, 2 fatal."""

import argparse
from pathlib import Path
import re

FATAL = re.compile(
    r'Mixin apply failed|MixinApplyError|InvalidMixinException|InjectionError|'
    r'Exception in thread|MOD LOADING ERROR|Failed to create mod instance|'
    r'Duplicate UID|Error loading plugin|Caught unhandled exception|'
    r'Data providers cannot implement|Mod .+ encountered an error|'
    r'Could not execute entrypoint|EntrypointException|'
    r'NoClassDefFoundError|NoSuchMethodError|BUILD FAILED', re.I)


def classify(text, mode, platform, jade=False):
    failure = FATAL.search(text)
    if failure:
        return 2, f"Fatal launch error: {failure.group()}"
    markers = [("Better Bees initialization", r"Better Bees initialization complete")]
    if platform != "neoforge":
        markers.append(("Fabric API loaded", r"(?:- fabric-api\s|\|\s*fabric-api\s*\|)"))
    if mode == "server":
        markers += [("dedicated server ready", r'Done \([0-9.]+s\)! For help'),
                    ("Better Bees server started", r"Better Bees active:")]
    else:
        markers += [("resource reload", r"Reloading ResourceManager:"),
                    ("block atlas", r"textures/atlas/blocks\.png-atlas"),
                    ("GUI atlas", r"textures/atlas/gui\.png-atlas")]
    if jade:
        markers.append(("Jade server provider registered", r"Better Bees Jade server registration complete"))
        if mode == "client":
            markers.append(("Jade client provider registered", r"Better Bees Jade client registration complete"))
    missing = [name for name, pattern in markers if not re.search(pattern, text)]
    return (1, "Waiting for: " + ", ".join(missing)) if missing else (0, "Healthy initialized state")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("mode", choices=("client", "server"))
    parser.add_argument("platform", choices=("neoforge", "fabric", "quilt"))
    parser.add_argument("--jade", action="store_true")
    args = parser.parse_args()
    status, message = classify(args.log.read_text(errors="replace"), args.mode, args.platform, args.jade)
    print(message)
    raise SystemExit(status)
