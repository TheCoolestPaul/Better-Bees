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


if __name__ == '__main__':
    unittest.main()
