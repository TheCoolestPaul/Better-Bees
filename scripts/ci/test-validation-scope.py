#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('scope', Path(__file__).with_name('validation-scope.py'))
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'commit.gpgsign', 'false')
        self.git('config', 'core.hooksPath', str(self.repo / 'no-hooks'))
        self.base = self.commit({'README.md': 'initial', 'src/code.java': 'code'})

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, stderr=subprocess.PIPE).decode().strip()

    def commit(self, files):
        for name, content in files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        self.git('add', '.')
        self.git('commit', '-qm', 'test')
        return self.git('rev-parse', 'HEAD')

    def required(self, ref, **kwargs):
        return scope.runtime_required(kwargs.pop('event', 'push'), kwargs.pop('base', self.base),
                                      ref, repo=self.repo, **kwargs)

    def test_whitelist(self):
        self.assertTrue(scope.documentation_only(['README.md', 'docs/deep/guide.md']))
        for paths in ([], ['CHANGELOG.md'], ['docs/image.png'], ['docs/guide.MD'],
                      ['README.md', 'src/code.java'], ['.github/workflows/ci.yml']):
            self.assertFalse(scope.documentation_only(paths))

    def test_docs_and_fallbacks(self):
        ref = self.commit({'README.md': 'updated', 'docs/deep/guide.md': 'guide'})
        for event in ('push', 'pull_request'):
            self.assertFalse(self.required(ref, event=event))
        for kwargs in ({'base': ''}, {'base': '0' * 40}, {'base': 'unavailable'},
                       {'event': 'workflow_dispatch'}, {'event': 'release'},
                       {'profile': 'full'}, {'version': '1.0.0'}):
            self.assertTrue(self.required(ref, **kwargs))
        self.assertTrue(self.required(self.base))
        self.assertTrue(self.required('unavailable'))
        with self.assertRaises(ValueError):
            self.required(ref, profile='unknown')

    def test_mixed_diff(self):
        ref = self.commit({'README.md': 'updated', 'src/code.java': 'updated'})
        self.assertTrue(self.required(ref, event='pull_request'))

    def test_pr_base_to_merge_revision(self):
        self.git('checkout', '-qb', 'feature')
        self.commit({'README.md': 'feature docs'})
        self.git('checkout', '-qb', 'base', self.base)
        new_base = self.commit({'src/code.java': 'base advanced'})
        self.git('merge', '--no-ff', '-qm', 'merge feature', 'feature')
        merge = self.git('rev-parse', 'HEAD')
        self.assertFalse(self.required(merge, event='pull_request', base=new_base))
        self.assertTrue(self.required(merge, event='pull_request', base=self.base))

    def test_code_renamed_to_docs(self):
        (self.repo / 'docs').mkdir()
        self.git('mv', 'src/code.java', 'docs/code.md')
        self.git('commit', '-qm', 'rename')
        self.assertTrue(self.required(self.git('rev-parse', 'HEAD')))

    def test_upgrade_sensitive_paths(self):
        for path in ('gradle/targets.json', 'settings.gradle', 'versions/26.2/build.gradle',
                     'fabric-versions/26.2/build.gradle',
                     'src/main/java/com/betterbees/registry/ModDataComponents.java',
                     'fabric/src/main/java/com/betterbees/registry/ModMemoryTypes.java',
                     'versions/1.21.8/src/main/java/com/betterbees/mixin/BeePersistenceMixin.java',
                     'versions/1.21.4/src/main/java/com/betterbees/platform/VersionHooks.java',
                     'src/main/java/com/betterbees/util/BeePersistentState.java',
                     'src/main/java/com/betterbees/hive/HiveHoneyStorage.java',
                     'src/main/resources/betterbees.mixins.json'):
            with self.subTest(path=path):
                self.assertTrue(scope.upgrade_sensitive([path]))
        self.assertFalse(scope.upgrade_sensitive([
            'README.md', 'gradle.properties',
            'scripts/ci/world-upgrade.sh',
            'src/main/java/com/betterbees/ai/HivePathQueue.java',
            'src/main/java/com/betterbees/audio/BeeLoopSelector.java',
            'src/main/java/com/betterbees/hive/HiveRuntimeState.java']))

    def upgrade(self, ref, **kwargs):
        return scope.world_upgrade_required(kwargs.pop('base', self.base), ref, repo=self.repo, **kwargs)

    def test_upgrade_opt_in_and_history_fallback(self):
        ref = self.commit({'src/code.java': 'ordinary change'})
        self.assertFalse(self.upgrade(ref))
        self.assertFalse(self.upgrade(self.base))
        self.assertTrue(self.upgrade(ref, requested=True))
        for base in ('', 'unavailable', '0' * 40):
            self.assertTrue(self.upgrade(ref, base=base))
        self.assertTrue(self.upgrade('unavailable'))

    def test_release_diff_includes_earlier_persistence_changes(self):
        self.git('tag', 'v1.0.0', self.base)
        persistence = self.commit({'src/main/java/com/betterbees/mixin/BeePersistenceMixin.java': 'saved data'})
        ref = self.commit({'README.md': 'release notes'})
        self.assertTrue(self.upgrade(ref, base='v1.0.0'))
        self.assertFalse(self.upgrade(ref, base=persistence))

    def test_removing_persistence_code_still_requires_upgrade(self):
        name = 'src/main/java/com/betterbees/mixin/BeePersistenceMixin.java'
        base = self.commit({name: 'saved data'})
        (self.repo / 'docs').mkdir()
        self.git('mv', name, 'docs/removed.md')
        self.git('commit', '-qm', 'remove persistence implementation')
        self.assertTrue(self.upgrade(self.git('rev-parse', 'HEAD'), base=base))

    def test_full_release_keeps_runtime_without_forcing_upgrade(self):
        ref = self.commit({'README.md': 'release notes'})
        self.assertTrue(self.required(ref, profile='full', version='1.0.1'))
        self.assertFalse(self.upgrade(ref))

    def test_cli_emits_independent_coverage_outputs(self):
        import sys
        ref = self.commit({'README.md': 'updated'})
        output = self.repo / 'output.txt'
        result = subprocess.check_output([
            sys.executable, str(Path(scope.__file__).resolve()), '--event', 'push',
            '--base', self.base, '--ref', ref, '--world-upgrade', 'true',
            '--github-output', str(output)], cwd=self.repo, text=True)
        self.assertEqual(result, 'runtime=false\nworld_upgrade=true\n')
        self.assertEqual(output.read_text(), result)

    def test_pr8_tooling_diff_skips_runtime_and_upgrades(self):
        paths = [
            '.github/workflows/ci.yml', '.github/workflows/release.yml',
            '.github/workflows/validate.yml', 'README.md', 'docs/release-app.md',
            'scripts/ci/gradle-with-retry.sh', 'scripts/ci/publish-release-git.py',
            'scripts/ci/test-gradle-with-retry.py', 'scripts/ci/test-publish-release-git.py',
            'scripts/ci/test-target-matrix.py', 'scripts/ci/test-validate-target.py',
            'scripts/ci/test-validation-scope.py', 'scripts/ci/validate-target.sh',
            'scripts/ci/validation-scope.py', 'scripts/ci/world-upgrade.sh',
        ]
        ref = self.commit(dict.fromkeys(paths, 'CI edit'))
        for event in ('pull_request', 'push'):
            self.assertFalse(self.required(ref, event=event))
        self.assertFalse(self.upgrade(ref))
        self.assertTrue(self.upgrade(ref, requested=True))
        for kwargs in ({'profile': 'full'}, {'version': '1.6.0'},
                       {'event': 'workflow_dispatch'}, {'base': 'missing'}):
            self.assertTrue(self.required(ref, **kwargs))

    def test_tooling_with_build_or_mod_changes_requires_runtime(self):
        for path in ('build.gradle', 'settings.gradle', 'gradle.properties',
                     'gradle/targets.json', 'scripts/ci/server-only.gradle',
                     'scripts/ci/server-assets.properties', 'src/code.java',
                     'src/main/resources/asset.json', '.github/modrinth-dependencies.json'):
            with self.subTest(path=path):
                self.assertFalse(scope.tooling_only(['scripts/ci/test-target-matrix.py', path]))
        ref = self.commit({'scripts/ci/test-target-matrix.py': 'test', 'src/code.java': 'changed'})
        self.assertTrue(self.required(ref))
        self.assertFalse(scope.tooling_only([]))

    def test_code_renamed_into_ci_still_requires_runtime(self):
        (self.repo / 'scripts/ci').mkdir(parents=True)
        self.git('mv', 'src/code.java', 'scripts/ci/removed.py')
        self.git('commit', '-qm', 'rename')
        self.assertTrue(self.required(self.git('rev-parse', 'HEAD')))


if __name__ == '__main__':
    unittest.main()
