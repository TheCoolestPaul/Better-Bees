#!/usr/bin/env python3
"""Skip runtime validation only for a positively identified documentation-only diff."""

import argparse
from pathlib import Path
import subprocess


def documentation_only(paths):
    return bool(paths) and all(path == 'README.md' or
                               (path.startswith('docs/') and path.endswith('.md')) for path in paths)


def runtime_required(event, base, ref, profile='routine', version='', repo='.'):
    if profile not in ('routine', 'full'):
        raise ValueError(f'Unknown validation profile: {profile}')
    if profile == 'full' or version or event not in ('pull_request', 'push') or not base or not ref:
        return True
    try:
        commits = []
        for revision in (base, ref):
            commits.append(subprocess.check_output(
                ['git', 'rev-parse', '--verify', '--end-of-options', revision + '^{commit}'],
                cwd=repo, stderr=subprocess.DEVNULL).decode().strip())
        # Report both sides of renames; moving executable code into docs must not skip tests.
        diff = subprocess.check_output(
            ['git', 'diff', '--no-ext-diff', '--no-renames', '--name-only', '-z', *commits, '--'],
            cwd=repo, stderr=subprocess.DEVNULL)
    except (subprocess.CalledProcessError, OSError, UnicodeError):
        return True
    paths = diff.decode('utf-8', errors='surrogateescape').split('\0')
    return not documentation_only([path for path in paths if path])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--event', required=True)
    parser.add_argument('--base', default='')
    parser.add_argument('--ref', required=True)
    parser.add_argument('--profile', choices=('routine', 'full'), default='routine')
    parser.add_argument('--version', default='')
    parser.add_argument('--github-output', type=Path)
    args = parser.parse_args()
    required = runtime_required(args.event, args.base, args.ref, args.profile, args.version)
    output = f'runtime={str(required).lower()}\n'
    print(output, end='')
    if args.github_output:
        with args.github_output.open('a', encoding='utf-8') as stream:
            stream.write(output)
