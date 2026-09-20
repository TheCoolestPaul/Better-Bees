#!/usr/bin/env python3
"""Atomically publish a version-only commit and immutable release tag."""
import os
from pathlib import Path
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def publish(source, version, tag):
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Expected a full validated source SHA')
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', version) or tag != f'v{version}':
        raise ValueError('Invalid release version or tag')
    if git('rev-parse', 'HEAD') != source:
        raise ValueError('Checkout differs from validated source')
    if git('status', '--porcelain', '--untracked-files=no'):
        raise ValueError('Tracked checkout must be clean before preparing the version')

    properties = Path('gradle.properties')
    data, count = re.subn(rb'(?m)^mod_version=[^\r\n]*',
                          b'mod_version=' + version.encode('ascii'), properties.read_bytes())
    if count != 1:
        raise ValueError('Expected exactly one mod_version property')
    properties.write_bytes(data)
    git('add', '--', 'gradle.properties')
    changed = git('diff', '--cached', '--name-only').splitlines()
    if changed not in ([], ['gradle.properties']):
        raise ValueError('Release may change only gradle.properties')
    expected_tree = git('write-tree')

    git('fetch', '--no-tags', 'origin', 'refs/heads/main')
    main = git('rev-parse', 'FETCH_HEAD')
    existing = git('ls-remote', '--refs', 'origin', f'refs/tags/{tag}')
    if existing:
        git('fetch', '--no-tags', 'origin', f'refs/tags/{tag}')
        commit = git('rev-parse', 'FETCH_HEAD^{commit}')
        if git('rev-parse', f'{commit}^{{tree}}') != expected_tree:
            raise ValueError('Existing tag does not match the validated release tree')
        if commit != source and git('show', '-s', '--format=%P', commit) != source:
            raise ValueError('Existing tag is not the validated source or its version-only child')
        if subprocess.run(['git', 'merge-base', '--is-ancestor', commit, main]).returncode:
            raise ValueError('Existing release commit is no longer on main')
        print(f'Reusing {tag} at {commit}')
        return commit

    if main != source:
        raise ValueError('main changed; start a new release run')
    git('config', 'user.name', os.environ['RELEASE_BOT_NAME'])
    git('config', 'user.email', os.environ['RELEASE_BOT_EMAIL'])
    if changed:
        git('commit', '-m', f'Release {version}')
    commit = git('rev-parse', 'HEAD')
    if git('rev-parse', 'HEAD^{tree}') != expected_tree:
        raise ValueError('Release commit differs from validated release tree')
    if commit != source and git('show', '-s', '--format=%P', commit) != source:
        raise ValueError('Release commit must directly follow validated source')
    if git('ls-remote', 'origin', 'refs/heads/main').split()[0] != source:
        raise ValueError('main changed before push; start a new release run')
    git('tag', '-a', tag, commit, '-m', f'Better Bees {version}')
    git('push', '--atomic', 'origin', f'{commit}:refs/heads/main', f'refs/tags/{tag}:refs/tags/{tag}')
    return commit


if __name__ == '__main__':
    os.chdir(Path(__file__).resolve().parents[2])
    try:
        output = Path(os.environ['GITHUB_OUTPUT'])
        commit = publish(os.environ['SHA'], os.environ['VERSION'], os.environ['TAG'])
        with output.open('a', encoding='utf-8') as stream:
            stream.write(f'commit={commit}\n')
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f'Release Git publication failed: {error}', file=sys.stderr)
        sys.exit(1)
