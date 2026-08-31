#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("target_matrix", Path(__file__).with_name("target-matrix.py"))
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)


class MatrixTests(unittest.TestCase):
    def test_balanced_coverage_and_launch_budget(self):
        rows = matrix.matrix("validation")
        self.assertEqual(len(rows), 32)
        keys = {(r['platform'], r['minecraft'], r['endpoint']) for r in rows}
        self.assertEqual(len(keys), len(rows))
        self.assertEqual(sum(3 if r['endpoint'] == 'latest' else 1 for r in rows), 64)
        for mc, target in matrix.TARGETS.items():
            for platform in ('neoforge', 'fabric', 'quilt'):
                supported = platform != 'quilt' or target.get('quiltSupported', True)
                for endpoint in ('floor', 'latest'):
                    self.assertEqual((platform, mc, endpoint) in keys, supported)

    def test_versions_come_from_manifest_and_jade_only_uses_latest(self):
        for row in matrix.matrix('validation'):
            target = matrix.TARGETS[row['minecraft']]
            prefix = {'neoforge': 'neo', 'fabric': 'fabricLoader', 'quilt': 'quiltLoader'}[row['platform']]
            self.assertEqual(row['loader'], target[prefix + row['endpoint'].title()])
            neo = row['platform'] == 'neoforge'
            self.assertEqual(row['jade'], target['jadeLatest' if neo else 'fabricJadeLatest'])
            self.assertEqual(row['api'], '' if neo else target['fabricApi' + row['endpoint'].title()])

    def test_package_targets_and_jade_support_remain_correct(self):
        rows = matrix.matrix('package')
        self.assertEqual(len(rows), 12)
        neo = next(r for r in rows if r['platform'] == 'neoforge' and r['minecraft'] == '1.21.4')
        fabric = next(r for r in rows if r['platform'] == 'fabric' and r['minecraft'] == '1.21.4')
        self.assertEqual(neo['jadeRange'], '[17.3.0,18)')
        self.assertEqual(fabric['jadeRange'], '[17.0.0,18)')


if __name__ == '__main__':
    unittest.main()
