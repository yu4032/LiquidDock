import copy
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest

from offline_patch import approved_sites, expected_patched
from verify_binary import sha256, verify


def fixture():
    image = bytearray(0x200)
    image[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", image, 18, 183)  # AArch64
    struct.pack_into("<Q", image, 32, 64)  # program header offset
    struct.pack_into("<H", image, 54, 56)
    struct.pack_into("<H", image, 56, 1)
    struct.pack_into("<IIQQQQQQ", image, 64, 1, 5, 0x100, 0x1000, 0, 0x40, 0x40, 0x1000)
    image[0x100 : 0x110] = bytes.fromhex("fd7bbfa9fd030091e00313aa00000014")
    function = bytes(image[0x100 : 0x110])
    manifest = {
        "binary": {"size": len(image), "sha256": sha256(image)},
        "ghidra_image_base": 0x100000,
        "functions": [
            {
                "name": "fixture",
                "ghidra_address": "0x101000",
                "elf_vaddr": "0x1000",
                "size": len(function),
                "sha256": sha256(function),
                "entry_bytes": function[:8].hex(),
            }
        ],
        "patch_sites": [
            {
                "name": "fixture-branch",
                "elf_vaddr": "0x100c",
                "before": "00000014",
                "after": "01000014",
            }
        ],
        "candidate_sites": [
            {
                "name": "fixture-branch-candidate",
                "elf_vaddr": "0x100c",
                "ghidra_address": "0x10100c",
                "bytes": "00000014",
            }
        ],
    }
    return bytes(image), manifest


class BinaryGuardsTest(unittest.TestCase):
    def test_exact_binary_and_function_identity(self):
        original, manifest = fixture()
        self.assertEqual([], verify(original, manifest))
        corrupted = bytearray(original)
        corrupted[0x10c] ^= 1
        failures = verify(bytes(corrupted), manifest)
        self.assertTrue(any("binary SHA256" in failure for failure in failures))
        self.assertTrue(any("function bytes" in failure for failure in failures))
        self.assertTrue(any("candidate instruction" in failure for failure in failures))

    def test_target_bytes_and_only_approved_changes(self):
        original, manifest = fixture()
        sites = approved_sites(manifest, original)
        patched = expected_patched(original, sites)
        self.assertEqual(len(original), len(patched))
        self.assertEqual([0x10c], [i for i, (a, b) in enumerate(zip(original, patched)) if a != b])
        self.assertEqual(bytes.fromhex("01000014"), patched[0x10c:0x110])
        wrong = copy.deepcopy(manifest)
        wrong["patch_sites"][0]["before"] = "ffffffff"
        with self.assertRaisesRegex(ValueError, "target instruction bytes differ"):
            approved_sites(wrong, original)

    def test_unapproved_plan_and_wrong_architecture_fail_closed(self):
        original, manifest = fixture()
        manifest["patch_sites"] = []
        with self.assertRaisesRegex(ValueError, "no approved patch sites"):
            approved_sites(manifest, original)
        wrong_arch = bytearray(original)
        struct.pack_into("<H", wrong_arch, 18, 62)
        self.assertTrue(any("AArch64" in failure for failure in verify(bytes(wrong_arch), manifest)))

    def test_offline_apply_verify_and_one_command_restore(self):
        original, manifest = fixture()
        expected = expected_patched(original, approved_sites(manifest, original))
        manifest["patched_sha256"] = sha256(expected)
        tool = Path(__file__).with_name("offline_patch.py")
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "original.so"
            output = directory / "patched.so"
            restored = directory / "restored.so"
            plan = directory / "manifest.json"
            source.write_bytes(original)
            plan.write_text(json.dumps(manifest))

            def run(operation, backup, destination):
                return subprocess.run(
                    [sys.executable, str(tool), operation, "--original", str(backup),
                     "--output", str(destination), "--manifest", str(plan)],
                    capture_output=True, text=True, check=True,
                )

            run("apply", source, output)
            backup = directory / "patched.so.original"
            self.assertEqual(original, backup.read_bytes())
            self.assertEqual(expected, output.read_bytes())
            run("verify-patched", backup, output)
            run("restore", backup, restored)
            self.assertEqual(original, restored.read_bytes())

            tampered = bytearray(expected)
            tampered[0x140] ^= 1  # outside the approved instruction
            output.write_bytes(tampered)
            with self.assertRaises(subprocess.CalledProcessError):
                run("verify-patched", backup, output)


if __name__ == "__main__":
    unittest.main()
