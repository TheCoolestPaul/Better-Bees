#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_smoke", Path(__file__).with_name("check-smoke.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

CLIENT = """
Better Bees initialization complete
- fabric-api 0.136.1+1.21.8
Reloading ResourceManager: vanilla, fabric, betterbees
Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas
Created: 1024x512x0 minecraft:textures/atlas/gui.png-atlas
"""
SERVER = """
Better Bees initialization complete
| Fabric API | fabric-api | 0.136.1+1.21.8 |
Better Bees active: hive capacity=20
Done (5.130s)! For help, type "help"
"""


class SmokeTests(unittest.TestCase):
    def test_healthy_fabric_does_not_need_neoforge_resource_name(self):
        self.assertEqual(checker.classify(CLIENT, "client", "fabric")[0], 0)

    def test_vanilla_quilt_cannot_pass(self):
        text = 'Reloading ResourceManager: vanilla\nDone (5.130s)! For help'
        status, message = checker.classify(text, "server", "quilt")
        self.assertEqual(status, 1)
        self.assertIn("Better Bees initialization", message)
        self.assertIn("Fabric API loaded", message)

    def test_quilt_server_with_real_mods_passes(self):
        self.assertEqual(checker.classify(SERVER, "server", "quilt")[0], 0)

    def test_jade_requires_completed_registration(self):
        text = CLIENT + '\nLoading plugin com.betterbees.compat.jade.BetterBeesJadePlugin'
        self.assertEqual(checker.classify(text, "client", "fabric", True)[0], 1)
        text += '\nBetter Bees Jade server registration complete'
        self.assertEqual(checker.classify(text, "client", "fabric", True)[0], 1)
        text += '\nBetter Bees Jade client registration complete'
        self.assertEqual(checker.classify(text, "client", "fabric", True)[0], 0)

    def test_deferred_jade_failure_overrides_healthy_markers(self):
        status, message = checker.classify(CLIENT + '\nData providers cannot implement IComponentProvider', "client", "fabric", True)
        self.assertEqual(status, 2)
        self.assertIn("Fatal launch error", message)

    def test_incomplete_startup_explains_what_is_missing(self):
        status, message = checker.classify(CLIENT.replace('textures/atlas/gui.png-atlas', ''), "client", "fabric")
        self.assertEqual((status, message), (1, "Waiting for: GUI atlas"))

    def test_optional_narrator_warning_is_not_a_mod_crash(self):
        self.assertEqual(checker.classify(CLIENT + "\nUnable to load library 'flite'", "client", "fabric")[0], 0)


if __name__ == "__main__":
    unittest.main()
