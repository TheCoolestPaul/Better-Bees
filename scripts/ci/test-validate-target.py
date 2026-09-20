#!/usr/bin/env python3
"""Run the real endpoint shell script against fake Gradle and client launchers."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('matrix', HERE / 'target-matrix.py')
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write('scripts/ci/validate-target.sh', (HERE / 'validate-target.sh').read_text())
        self.write('scripts/ci/gradle-with-retry.sh', (HERE / 'gradle-with-retry.sh').read_text())
        self.write('src/main/java/com/betterbees/gametest/BetterBeesGameTests.java', '@GameTest(\n@GameTest(')
        self.write('gradlew', '#!/usr/bin/env bash\nprintf "%s\\n" "$@" >> gradle-args\n'
                   'echo "${TEST_MESSAGE-All 2 required tests passed}"\nexit "${GRADLE_EXIT-0}"\n')
        self.write('scripts/ci/smoke-launch.sh', '#!/usr/bin/env bash\nprintf "%s\\n" "$@" >> smoke-args\nexit "${SMOKE_EXIT-0}"\n')
        self.write('bin/python3', '#!/usr/bin/env bash\nexec "' + Path(sys.executable).as_posix() + '" "$@"\n')

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, newline='\n')
        path.chmod(0o755)

    def run_target(self, row, **extra):
        for name in ('gradle-args', 'smoke-args'):
            (self.root / name).unlink(missing_ok=True)
        env = dict(os.environ, TARGET=json.dumps(row), **extra)
        env['PATH'] = str(self.root / 'bin') + os.pathsep + env['PATH']
        result = subprocess.run([BASH, 'scripts/ci/validate-target.sh'], cwd=self.root,
                                env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        args = lambda name: (self.root / name).read_text().splitlines() if (self.root / name).exists() else []
        return result, args('gradle-args'), args('smoke-args')

    def test_all_profile_rows(self):
        for profile, expected_launches in (('routine', 0), ('full', 32)):
            launches = 0
            for row in matrix.matrix('validation', profile):
                with self.subTest(row=row):
                    result, gradle, smoke = self.run_target(row)
                    self.assertEqual(result.returncode, 0, result.stdout)
                    project = row['project']
                    self.assertEqual(f':{project}:build' in gradle, row['platform'] != 'quilt')
                    task = 'runGameTestServer' if row['platform'] == 'neoforge' else 'runGameTest'
                    self.assertIn(f':{project}:{task}', gradle)
                    self.assertNotIn('performancePolicyTest', gradle)
                    self.assertFalse(any('withJade' in arg or 'withCreate' in arg for arg in gradle))
                    self.assertEqual('-PwithQuilt=true' in gradle, row['platform'] == 'quilt')
                    self.assertEqual('--init-script' in gradle,
                                     profile == 'routine' and row['platform'] == 'neoforge')
                    self.assertEqual(bool(smoke), profile == 'full')
                    if smoke:
                        self.assertEqual(smoke[:4], ['client', row['platform'], project, row['loader']])
                    launches += bool(smoke)
            self.assertEqual(launches, expected_launches)

    def test_failures(self):
        row = matrix.matrix('validation', 'full')[0]
        for extra in ({'GRADLE_EXIT': '7'}, {'TEST_MESSAGE': 'BUILD SUCCESSFUL'},
                      {'TEST_MESSAGE': 'All 1 required tests passed'}):
            result, _, smoke = self.run_target(row, **extra)
            self.assertNotEqual(result.returncode, 0, result.stdout)
            self.assertFalse(smoke)
        result, _, smoke = self.run_target(row, SMOKE_EXIT='9')
        self.assertEqual(result.returncode, 9)
        self.assertTrue(smoke)

    def test_invalid_profile(self):
        row = dict(matrix.matrix('validation')[0], profile='unknown')
        result, gradle, smoke = self.run_target(row)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(gradle or smoke)


if __name__ == '__main__':
    unittest.main()
