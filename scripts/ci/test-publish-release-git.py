#!/usr/bin/env python3
"""Exercise release publication against disposable local Git remotes."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.remote = self.root / 'remote.git'
        self.git('init', '-q', '-b', 'main')
        for key, value in [('user.name', 'Test'), ('user.email', 'test@example.invalid'),
                           ('commit.gpgsign', 'false'), ('tag.gpgsign', 'false'),
                           ('core.autocrlf', 'false'), ('core.hooksPath', str(self.root / 'no-hooks'))]:
            self.git('config', key, value)
        ci = self.repo / 'scripts/ci'
        ci.mkdir(parents=True)
        shutil.copyfile(HERE / 'publish-release-git.py', ci / 'publish-release-git.py')
        (self.repo / 'gradle.properties').write_bytes(b'mod_version=1.5.0\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'source')
        self.source = self.git('rev-parse', 'HEAD')
        self.git('init', '-q', '--bare', str(self.remote))
        self.git('remote', 'add', 'origin', str(self.remote))
        self.git('push', '-q', 'origin', 'main')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True, stderr=subprocess.PIPE).strip()

    def remote_git(self, *args):
        return self.git('--git-dir', str(self.remote), *args)

    def publish(self, version='1.6.0', source=None):
        return subprocess.run([sys.executable, 'scripts/ci/publish-release-git.py'], cwd=self.repo,
            env=dict(os.environ, SHA=source or self.source, VERSION=version, TAG=f'v{version}',
                     GITHUB_OUTPUT=str(self.root / 'output'), RELEASE_BOT_NAME='release[bot]',
                     RELEASE_BOT_EMAIL='1+release[bot]@users.noreply.github.com'),
            capture_output=True, text=True, timeout=20)

    def success(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_commit_and_tag_match_validated_tree(self):
        self.success(self.publish())
        commit = self.remote_git('rev-parse', 'main')
        self.assertEqual(self.remote_git('rev-parse', 'v1.6.0^{}'), commit)
        self.assertEqual(self.remote_git('rev-parse', f'{commit}^'), self.source)
        self.assertEqual(self.remote_git('diff', '--name-only', self.source, commit), 'gradle.properties')
        self.assertEqual(self.remote_git('show', f'{commit}:gradle.properties'), 'mod_version=1.6.0')
        self.assertEqual(self.remote_git('show', '-s', '--format=%an', commit), 'release[bot]')
        self.assertIn('[skip ci]', self.remote_git('show', '-s', '--format=%B', commit))

    def test_retry_after_push_reuses_commit(self):
        self.success(self.publish())
        commit = self.remote_git('rev-parse', 'main')
        tag = self.remote_git('rev-parse', 'v1.6.0')
        self.git('checkout', '-q', '--detach', self.source)
        self.success(self.publish())
        self.assertEqual(self.remote_git('rev-parse', 'main'), commit)
        self.assertEqual(self.remote_git('rev-parse', 'v1.6.0'), tag)
        self.git('reset', '--hard', commit)
        self.success(self.publish(source=commit))

    def test_current_version_does_not_create_commit(self):
        self.success(self.publish(version='1.5.0'))
        self.assertEqual(self.remote_git('rev-parse', 'main'), self.source)

    def test_matching_lightweight_tag(self):
        self.git('tag', 'v1.5.0')
        self.git('push', '-q', 'origin', 'refs/tags/v1.5.0')
        self.success(self.publish(version='1.5.0'))

    def test_conflicting_tag_does_not_move(self):
        self.git('tag', 'v1.6.0')
        self.git('push', '-q', 'origin', 'refs/tags/v1.6.0')
        self.assertNotEqual(self.publish().returncode, 0)
        self.assertEqual(self.remote_git('rev-parse', 'v1.6.0'), self.source)

    def test_main_advanced(self):
        self.git('commit', '--allow-empty', '-qm', 'another change')
        self.git('push', '-q', 'origin', 'main')
        self.git('checkout', '-q', '--detach', self.source)
        self.assertIn('main changed', self.publish().stderr)
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/tags/v1.6.0'), '')

    def test_dirty_checkout_rejected(self):
        (self.repo / 'gradle.properties').write_text('mod_version=9.0.0\n')
        self.assertIn('must be clean', self.publish().stderr)

    def test_unexpected_staged_file_rejected(self):
        (self.repo / 'unexpected.txt').write_text('must not publish')
        self.git('add', 'unexpected.txt')
        self.assertIn('must be clean', self.publish().stderr)
        self.assertEqual(self.remote_git('rev-parse', 'main'), self.source)

    def test_tag_not_on_main_rejected(self):
        (self.repo / 'gradle.properties').write_bytes(b'mod_version=1.6.0\n')
        self.git('commit', '-am', 'version')
        self.git('tag', 'v1.6.0')
        self.git('push', '-q', 'origin', 'refs/tags/v1.6.0')
        self.git('checkout', '-q', '--detach', self.source)
        self.assertIn('no longer on main', self.publish().stderr)

    def test_wrong_checkout_rejected(self):
        self.git('commit', '--allow-empty', '-qm', 'different source')
        self.assertIn('Checkout differs', self.publish().stderr)

    def test_atomic_rejection_changes_neither_ref(self):
        hooks = self.remote / 'hooks'
        self.remote_git('config', 'core.hooksPath', hooks.as_posix())
        hook = hooks / 'update'
        hook.write_text('#!/bin/sh\n[ "$1" != refs/heads/main ]\n', newline='\n')
        hook.chmod(0o755)
        self.assertNotEqual(self.publish().returncode, 0)
        self.assertEqual(self.remote_git('rev-parse', 'main'), self.source)
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/tags/v1.6.0'), '')

    def test_equal_tree_with_wrong_parent_rejected(self):
        self.git('commit', '--allow-empty', '-qm', 'intermediate')
        (self.repo / 'gradle.properties').write_bytes(b'mod_version=1.6.0\n')
        self.git('commit', '-am', 'version')
        self.git('tag', 'v1.6.0')
        self.git('push', '-q', 'origin', 'main', 'refs/tags/v1.6.0')
        self.git('checkout', '-q', '--detach', self.source)
        self.assertIn('version-only child', self.publish().stderr)


if __name__ == '__main__':
    unittest.main()
