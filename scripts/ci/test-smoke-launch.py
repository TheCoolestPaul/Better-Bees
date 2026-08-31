#!/usr/bin/env python3
"""Exercise the real shell harness using a disposable fake game process."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('bash') and shutil.which('setsid'), 'requires bash and setsid')
class HarnessTests(unittest.TestCase):
    def run_smoke(self, scenario, mode='server'):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ci = root / 'scripts' / 'ci'
            ci.mkdir(parents=True)
            for name in ('smoke-launch.sh', 'check-smoke.py'):
                shutil.copyfile(ROOT / 'scripts' / 'ci' / name, ci / name)
            launch = root / 'gradlew'
            launch.write_text('''#!/usr/bin/env bash
set -eu
echo "$*" >> calls.txt
if [[ "$*" == *:downloadAssets* ]]; then
  if [[ "$SCENARIO" == asset_retry && ! -e tried ]]; then
    touch tried
    echo "Execution failed for task ':fabricMc1_21_8:downloadAssets' (registered by plugin 'fabric-loom')."
    exit 1
  fi
  if [[ "$SCENARIO" == config_failure ]]; then
    echo 'Could not resolve build plugin'
    exit 1
  fi
  exit 0
fi
if [[ "$SCENARIO" == timeout ]]; then
  echo 'Better Bees initialization complete'
elif [[ "$SCENARIO" == fatal ]]; then
  echo 'Data providers cannot implement IComponentProvider'
else
  echo 'Better Bees initialization complete'
  echo '- fabric-api 0.136.1+1.21.8'
  echo 'Better Bees active: hive capacity=20'
  echo 'Done (1.0s)! For help'
  echo 'Reloading ResourceManager: vanilla, fabric, betterbees'
  echo 'textures/atlas/blocks.png-atlas'
  echo 'textures/atlas/gui.png-atlas'
fi
while true; do sleep 1; done
''', newline='\n')
            launch.chmod(0o755)
            # No graphics are needed by the fake client.
            xvfb = root / 'xvfb-run'
            xvfb.write_text('#!/usr/bin/env bash\nshift\nexec "$@"\n', newline='\n')
            xvfb.chmod(0o755)
            env = dict(os.environ, SCENARIO=scenario, SMOKE_TIMEOUT_SECONDS='10' if scenario == 'asset_retry' else '3')
            env['PATH'] = str(root) + os.pathsep + env['PATH']
            result = subprocess.run([shutil.which('bash'), str(ci / 'smoke-launch.sh'), mode, 'fabric', 'fabricMc1_21_8', '0.19.5'],
                                    env=env, capture_output=True, text=True, timeout=30)
            self.assertTrue((root / 'calls.txt').exists(), result.stdout + result.stderr)
            calls = (root / 'calls.txt').read_text().splitlines()
            return result, calls

    def test_timeout_reports_missing_evidence(self):
        result, calls = self.run_smoke('timeout')
        self.assertEqual(result.returncode, 1)
        self.assertIn('Timed out after 3s', result.stderr)
        self.assertIn('Fabric API loaded', result.stderr)
        self.assertEqual(len(calls), 1)

    def test_mod_failure_is_not_retried(self):
        result, calls = self.run_smoke('fatal')
        self.assertEqual(result.returncode, 1)
        self.assertIn('Fatal launch error', result.stderr)
        self.assertEqual(len(calls), 1)

    def test_asset_failure_retries_only_preparation(self):
        result, calls = self.run_smoke('asset_retry', 'client')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(sum(':downloadAssets' in call for call in calls), 2)
        self.assertEqual(sum(':runClient' in call for call in calls), 1)

    def test_configuration_failure_is_not_retried(self):
        result, calls = self.run_smoke('config_failure', 'client')
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len(calls), 1)
        self.assertIn(':downloadAssets', calls[0])


if __name__ == '__main__':
    unittest.main()
