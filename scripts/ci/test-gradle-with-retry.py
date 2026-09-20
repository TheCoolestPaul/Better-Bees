#!/usr/bin/env python3
"""Exercise retry boundaries using the real shell wrapper and a fake Gradle."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')


class RetryTests(unittest.TestCase):
    def run_wrapper(self, failure, *, recover=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ci = root / 'scripts' / 'ci'
            ci.mkdir(parents=True)
            shutil.copyfile(HERE / 'gradle-with-retry.sh', ci / 'gradle-with-retry.sh')
            (root / 'gradlew').write_text('''#!/usr/bin/env bash
set -eu
echo call >> calls
printf '%s\\n' "$@" > arguments
if [[ "$RECOVER" == true && $(wc -l < calls) -gt 1 ]]; then
  echo '> Task :runServer'
  echo 'BUILD SUCCESSFUL'
  exit 0
fi
printf '%s\\n' "$FAILURE"
exit 7
''', newline='\n')
            (root / 'gradlew').chmod(0o755)
            # Record backoff without slowing tests down.
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            (bin_dir / 'sleep').write_text('#!/usr/bin/env bash\necho "$1" >> delays\n', newline='\n')
            (bin_dir / 'sleep').chmod(0o755)
            env = dict(os.environ, FAILURE=failure, RECOVER=str(recover).lower())
            env['PATH'] = str(bin_dir) + os.pathsep + env['PATH']
            result = subprocess.run([BASH, '-c',
                                     'export PATH="$PWD/bin:$PATH"; exec bash scripts/ci/gradle-with-retry.sh "$@"',
                                     'test', '--no-daemon',
                                     '-PsmokeGameDirectory=directory with spaces', ':module:runServer'],
                                    cwd=root, env=env, capture_output=True, text=True, timeout=15)
            calls = len((root / 'calls').read_text().splitlines())
            delays = (root / 'delays').read_text().splitlines() if (root / 'delays').exists() else []
            self.assertIn('-PsmokeGameDirectory=directory with spaces', (root / 'arguments').read_text().splitlines())
            return result, calls, list(map(int, delays))

    def download_failure(self, status):
        return ("A problem occurred configuring project ':fabricMc26_1_2'.\n"
                "> Could not GET 'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar'. "
                f'Received status code {status} from server: unavailable')

    def test_transient_failure_recovers(self):
        for status in (429, 500, 502, 503, 504):
            with self.subTest(status=status):
                result, calls, delays = self.run_wrapper(self.download_failure(status), recover=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(calls, 2)
                self.assertTrue(15 <= delays[0] <= 20)

    def test_retries_are_bounded_and_preserve_failure(self):
        result, calls, delays = self.run_wrapper(self.download_failure(429))
        self.assertEqual(result.returncode, 7)
        self.assertEqual(calls, 3)
        self.assertEqual(len(delays), 2)
        self.assertTrue(30 <= delays[1] <= 35)

    def test_permanent_errors_are_not_retried(self):
        for status in (400, 401, 403, 404):
            with self.subTest(status=status):
                result, calls, delays = self.run_wrapper(self.download_failure(status))
                self.assertEqual((result.returncode, calls, delays), (7, 1, []))

    def test_task_execution_and_unrelated_failures_are_not_retried(self):
        for failure in ('Compilation failed', 'All 0 required tests passed',
                        "A problem occurred configuring project ':module'.\nUnknown property",
                        '> Task :module:runServer\n' + self.download_failure(429),
                        self.download_failure(429).replace('A problem occurred configuring', 'Error running')):
            with self.subTest(failure=failure):
                result, calls, delays = self.run_wrapper(failure)
                self.assertEqual((result.returncode, calls, delays), (7, 1, []))


if __name__ == '__main__':
    unittest.main()
