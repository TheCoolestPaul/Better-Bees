#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("target_matrix", Path(__file__).with_name("target-matrix.py"))
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)


class MatrixTests(unittest.TestCase):
    def test_balanced_coverage_and_launch_budget(self):
        rows = matrix.matrix("validation", "full")
        self.assertEqual(len(rows), 32)
        keys = {(r['platform'], r['minecraft'], r['endpoint']) for r in rows}
        self.assertEqual(len(keys), len(rows))
        runner = (matrix.ROOT / 'scripts/ci/validate-target.sh').read_text()
        self.assertEqual(runner.count('bash scripts/ci/smoke-launch.sh'), 1)
        self.assertNotIn('-PwithJade', runner)
        self.assertNotIn('-PwithCreate', runner)
        self.assertNotIn('performancePolicyTest', runner)
        for mc, target in matrix.TARGETS.items():
            for platform in ('neoforge', 'fabric', 'quilt'):
                supported = platform != 'quilt' or target.get('quiltSupported', True)
                for endpoint in ('floor', 'latest'):
                    self.assertEqual((platform, mc, endpoint) in keys, supported)

    def test_versions_come_from_manifest_without_jade_runtime(self):
        for row in matrix.matrix('validation', 'full'):
            target = matrix.TARGETS[row['minecraft']]
            prefix = {'neoforge': 'neo', 'fabric': 'fabricLoader', 'quilt': 'quiltLoader'}[row['platform']]
            self.assertEqual(row['loader'], target[prefix + row['endpoint'].title()])
            neo = row['platform'] == 'neoforge'
            self.assertNotIn('jade', row)
            self.assertEqual(row['api'], '' if neo else target['fabricApi' + row['endpoint'].title()])

    def test_routine_is_twelve_minimum_artifact_targets(self):
        rows = matrix.matrix('validation')
        self.assertEqual(len(rows), 12)
        self.assertEqual({(r['platform'], r['minecraft']) for r in rows},
                         {(p, mc) for p in ('neoforge', 'fabric') for mc in matrix.TARGETS})
        for row in rows:
            self.assertEqual(row['profile'], 'routine')
            self.assertEqual(row['endpoint'], 'floor')
            target = matrix.TARGETS[row['minecraft']]
            neo = row['platform'] == 'neoforge'
            self.assertEqual(row['loader'], target['neoFloor' if neo else 'fabricLoaderFloor'])
            self.assertEqual(row['api'], '' if neo else target['fabricApiFloor'])

    def test_invalid_profile(self):
        with self.assertRaises(ValueError):
            matrix.matrix('validation', 'unknown')
        result = subprocess.run([sys.executable, str(Path(matrix.__file__)), 'validation',
                                 '--profile', 'unknown'], capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_workflow_routing(self):
        workflow = (matrix.ROOT / '.github/workflows/validate.yml').read_text()
        self.assertEqual(workflow.count('performancePolicyTest'), 1)
        tooling = workflow.split('  endpoint:')[0]
        for step in ('uses: actions/setup-java@v5', 'uses: gradle/actions/setup-gradle@v6',
                     'name: Shared performance policy'):
            self.assertIn(step + "\n        if: steps.scope.outputs.runtime == 'true'", tooling)
        self.assertIn("if: needs.tooling.outputs.world_upgrade == 'true'", workflow)
        self.assertNotIn("if: inputs.profile == 'full' && needs.tooling.outputs.runtime == 'true'", workflow)
        self.assertIn('platform: [neoforge, fabric, quilt]', workflow)
        self.assertIn("if: inputs.profile == 'full'\n", workflow)
        release = (matrix.ROOT / '.github/workflows/release.yml').read_text()
        self.assertIn('profile: full', release)
        self.assertIn('base_ref: ${{ needs.resolve.outputs.base_ref }}', release)
        self.assertIn('base_ref: ${{ steps.history.outputs.tag }}', release)
        self.assertIn('world_upgrade: ${{ inputs.world_upgrade }}', release)
        self.assertIn('needs: [resolve, validation]', release)
        ci = (matrix.ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.profile || 'routine'", ci)
        self.assertIn('github.event.pull_request.base.sha || github.event.before', ci)
        self.assertIn('cancel-in-progress: true', ci)
        self.assertIn('inputs.world_upgrade', ci)
        self.assertNotIn('paths-ignore:', ci)
        self.assertNotIn('paths:', ci)
        for text in (workflow, ci, release):
            self.assertNotIn('withJade', text)
            self.assertNotIn('withCreate', text)

    def test_package_targets_and_jade_support_remain_correct(self):
        rows = matrix.matrix('package')
        self.assertEqual(len(rows), 12)
        neo = next(r for r in rows if r['platform'] == 'neoforge' and r['minecraft'] == '1.21.4')
        fabric = next(r for r in rows if r['platform'] == 'fabric' and r['minecraft'] == '1.21.4')
        self.assertEqual(neo['jadeRange'], '[17.3.0,18)')
        self.assertEqual(fabric['jadeRange'], '[17.0.0,18)')

    def test_release_dependencies_match_packaged_integrations(self):
        for row in matrix.matrix('package'):
            with self.subTest(minecraft=row['minecraft'], platform=row['platform']):
                deps = {d['project_id']: d for d in row['dependencies']}
                expected = {'nvQzSEkH': 'optional'}
                if row['platform'] == 'fabric':
                    expected['P7dR8mSH'] = 'required'
                if row['minecraft'] == '1.21.1':
                    if row['platform'] == 'neoforge':
                        expected.update(LNytGWDc='optional', **{'38tpSycf': 'optional'})
                        self.assertEqual(deps['LNytGWDc']['version_range'], '[6.0.10,6.1)')
                        self.assertEqual(deps['38tpSycf']['version_range'], '[7.16.1,7.17)')
                    else:
                        expected['eA8SXqWL'] = 'optional'
                        self.assertEqual(deps['eA8SXqWL']['version_range'], '[7.16.0,7.17)')
                        self.assertEqual(row['loaders'], 'fabric quilt')
                self.assertEqual({key: d['dependency_type'] for key, d in deps.items()}, expected)
                with tempfile.TemporaryDirectory() as temporary:
                    manifest = Path(temporary) / 'dependencies.json'
                    manifest.write_text(json.dumps({'dependencies': row['dependencies']}))
                    result = subprocess.run([
                        sys.executable, str(matrix.ROOT / 'scripts/ci/validate-modrinth-release.py'),
                        '--manifest', str(manifest), '--jade-version-range', row['jadeRange'],
                        '--expected-version', '1.0.0', '--game-version', row['minecraft'],
                        '--version-type', 'release'], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
