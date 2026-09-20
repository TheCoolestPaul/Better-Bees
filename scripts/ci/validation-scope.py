#!/usr/bin/env python3
"""Select runtime and saved-world upgrade coverage from the changed files."""

import argparse
from pathlib import Path
import subprocess
from fnmatch import fnmatchcase


# Persistence implementations exist in shared, loader, and version source trees.
# Keep this list alongside changes that introduce new saved-data code.
UPGRADE_PATHS = (
    'gradle/targets.json', 'settings.gradle',
    'versions/*/build.gradle', 'fabric-versions/*/build.gradle',
    '**/registry/*.java', '**/platform/VersionHooks.java',
    '**/*Persistence*.java', '**/util/BeePersistentState.java',
    '**/hive/HiveHoney*.java', '**/validation/UpgradeFixture.java',
    '**/*.mixins.json', 'scripts/ci/world-upgrade.sh',
)


def upgrade_sensitive(paths):
    return any(fnmatchcase(path, pattern) for path in paths for pattern in UPGRADE_PATHS)


def changed_paths(base, ref, repo='.'):
    if not base or not ref:
        return None
    try:
        commits = []
        for revision in (base, ref):
            commits.append(subprocess.check_output(
                ['git', 'rev-parse', '--verify', '--end-of-options', revision + '^{commit}'],
                cwd=repo, stderr=subprocess.DEVNULL).decode().strip())
        # Include both sides of renames, including removed persistence code.
        diff = subprocess.check_output(
            ['git', 'diff', '--no-ext-diff', '--no-renames', '--name-only', '-z', *commits, '--'],
            cwd=repo, stderr=subprocess.DEVNULL)
        return [path for path in diff.decode('utf-8', errors='surrogateescape').split('\0') if path]
    except (subprocess.CalledProcessError, OSError, UnicodeError):
        return None


def world_upgrade_required(base, ref, requested=False, repo='.'):
    if requested:
        return True
    paths = changed_paths(base, ref, repo)
    # No previous release/history: establish coverage rather than guess.
    return paths is None or upgrade_sensitive(paths)


def documentation_only(paths):
    return bool(paths) and all(path == 'README.md' or
                               (path.startswith('docs/') and path.endswith('.md')) for path in paths)


def runtime_required(event, base, ref, profile='routine', version='', repo='.'):
    if profile not in ('routine', 'full'):
        raise ValueError(f'Unknown validation profile: {profile}')
    if profile == 'full' or version or event not in ('pull_request', 'push') or not base or not ref:
        return True
    paths = changed_paths(base, ref, repo)
    return paths is None or not documentation_only(paths)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--event', required=True)
    parser.add_argument('--base', default='')
    parser.add_argument('--ref', required=True)
    parser.add_argument('--profile', choices=('routine', 'full'), default='routine')
    parser.add_argument('--version', default='')
    parser.add_argument('--world-upgrade', choices=('true', 'false'), default='false')
    parser.add_argument('--github-output', type=Path)
    args = parser.parse_args()
    required = runtime_required(args.event, args.base, args.ref, args.profile, args.version)
    upgrade = world_upgrade_required(args.base, args.ref, args.world_upgrade == 'true')
    output = f'runtime={str(required).lower()}\nworld_upgrade={str(upgrade).lower()}\n'
    print(output, end='')
    if args.github_output:
        with args.github_output.open('a', encoding='utf-8') as stream:
            stream.write(output)
