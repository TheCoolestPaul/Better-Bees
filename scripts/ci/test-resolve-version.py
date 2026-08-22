#!/usr/bin/env python3

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("resolve-version.py")
SPEC = importlib.util.spec_from_file_location("resolve_version", MODULE_PATH)
resolve_version = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = resolve_version
SPEC.loader.exec_module(resolve_version)
Version = resolve_version.Version
resolve = resolve_version.resolve
modrinth_version_type = resolve_version.modrinth_version_type


class VersionTests(unittest.TestCase):
    def test_expected_bumps(self):
        self.assertEqual(str(resolve("1.0.0", "none", "current", "")), "1.0.0")
        self.assertEqual(str(resolve("1.0.0", "none", "patch", "")), "1.0.1")
        self.assertEqual(str(resolve("1.0.0", "none", "minor", "")), "1.1.0")
        self.assertEqual(str(resolve("1.0.0", "none", "major", "")), "2.0.0")

    def test_bump_uses_greater_baseline(self):
        self.assertEqual(str(resolve("1.0.0", "1.2.3", "minor", "")), "1.3.0")

    def test_prerelease_and_ordering(self):
        target = resolve("1.0.0", "none", "custom", "1.1.0-beta.1")
        self.assertEqual(str(target), "1.1.0-beta.1")
        self.assertTrue(target.prerelease)
        self.assertLess(Version.parse("1.1.0-beta.1").compare(Version.parse("1.1.0")), 0)
        self.assertEqual(modrinth_version_type(target), "beta")
        self.assertEqual(modrinth_version_type(Version.parse("1.1.0")), "release")

    def test_invalid_versions(self):
        for value in ("v1.1.0", "1.1", "01.1.0", "1.1.0+build", "1.1.0-01"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                Version.parse(value)

    def test_backward_release_rejected_but_same_release_is_recoverable(self):
        with self.assertRaises(ValueError):
            resolve("1.0.0", "1.1.0", "custom", "1.0.1")
        self.assertEqual(str(resolve("1.0.0", "1.0.0", "current", "")), "1.0.0")

    def test_custom_requires_value(self):
        with self.assertRaises(ValueError):
            resolve("1.0.0", "none", "custom", "")


if __name__ == "__main__":
    unittest.main()
