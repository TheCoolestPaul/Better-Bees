#!/usr/bin/env python3

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("validate-modrinth-release.py")
SPEC = importlib.util.spec_from_file_location("validate_modrinth_release", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class ManifestValidationTests(unittest.TestCase):
    def write_manifest(self, payload):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "dependencies.json"
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    @staticmethod
    def valid_payload():
        return {
            "dependencies": [
                {
                    "name": "Jade",
                    "project_id": "nvQzSEkH",
                    "dependency_type": "optional",
                    "version_range": "[15.1.6,16)",
                }
            ]
        }

    def test_valid_optional_jade_manifest(self):
        dependencies = validator.load_manifest(
            self.write_manifest(self.valid_payload()), "[15.1.6,16)"
        )
        self.assertEqual(1, len(dependencies))

    def test_range_must_match_mod_metadata(self):
        with self.assertRaisesRegex(validator.ValidationError, "differs"):
            validator.load_manifest(self.write_manifest(self.valid_payload()), "[15.2.0,16)")

    def test_jade_must_remain_optional(self):
        payload = self.valid_payload()
        payload["dependencies"][0]["dependency_type"] = "required"
        with self.assertRaisesRegex(validator.ValidationError, "optional"):
            validator.load_manifest(self.write_manifest(payload), "[15.1.6,16)")

    def test_supported_version_range(self):
        self.assertTrue(validator.version_in_range("15.1.6+neoforge", "[15.1.6,16)"))
        self.assertTrue(validator.version_in_range("15.10.6+neoforge", "[15.1.6,16)"))
        self.assertFalse(validator.version_in_range("15.1.5+neoforge", "[15.1.6,16)"))
        self.assertFalse(validator.version_in_range("16.0.0+neoforge", "[15.1.6,16)"))


class ExistingVersionValidationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.artifact = Path(temporary.name) / "betterbees-1.0.0.jar"
        self.artifact.write_bytes(b"release jar")
        self.dependencies = [
            {
                "name": "Jade",
                "project_id": "nvQzSEkH",
                "dependency_type": "optional",
                "version_range": "[15.1.6,16)",
            }
        ]

    def version_payload(self):
        return {
            "name": "Better Bees 1.0.0",
            "version_type": "release",
            "loaders": ["neoforge"],
            "game_versions": ["1.21.1"],
            "files": [
                {
                    "primary": True,
                    "filename": self.artifact.name,
                    "hashes": {"sha512": validator.sha512(self.artifact)},
                }
            ],
            "dependencies": [
                {
                    "project_id": "nvQzSEkH",
                    "version_id": None,
                    "dependency_type": "optional",
                }
            ],
        }

    def validate(self, payload):
        validator.validate_existing_version(
            payload,
            "Better Bees 1.0.0",
            self.artifact,
            "release",
            "neoforge",
            "1.21.1",
            self.dependencies,
        )

    def test_matching_release_is_safe_to_reuse(self):
        self.validate(self.version_payload())

    def test_conflicting_jar_is_rejected(self):
        payload = self.version_payload()
        payload["files"][0]["hashes"]["sha512"] = "0" * 128
        with self.assertRaisesRegex(validator.ValidationError, "differs"):
            self.validate(payload)

    def test_exact_jade_pin_is_rejected(self):
        payload = self.version_payload()
        payload["dependencies"][0]["version_id"] = "specificJadeVersion"
        with self.assertRaisesRegex(validator.ValidationError, "exact-version"):
            self.validate(payload)

    def test_prerelease_type_must_match(self):
        payload = self.version_payload()
        payload["version_type"] = "beta"
        with self.assertRaisesRegex(validator.ValidationError, "version type"):
            self.validate(payload)


if __name__ == "__main__":
    unittest.main()
