"""Isolated synthetic ELF tests; do not rely on copies of vendor libraries."""
import json
from pathlib import Path
import struct
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from attest_elf import attest, sha256


def fixture():
    data = bytearray(512)
    data[:16] = b"\x7fELF\x02\x01\x01" + bytes(9)
    struct.pack_into("<HHIQQQIHHHHHH", data, 16,
                     3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
    struct.pack_into("<IIQQQQQQ", data, 64,
                     1, 5, 0, 0, 0, 512, 512, 4096)
    data[256:320] = b"ABCD" + b"K" * 60
    binary = bytes(data)
    profile = {
        "elf_sha256": sha256(binary),
        "targets": [
            {"name": "synthetic.fn", "vaddr": "0x100",
             "size": 64, "sha256": sha256(binary[256:320])}
        ],
    }
    return binary, profile


class ElfAttestationTests(unittest.TestCase):
    def test_valid_fixture(self):
        binary, profile = fixture()
        self.assertEqual(attest(binary, profile), 1)

    def test_full_hash_catches_same_prologue(self):
        binary, profile = fixture()
        bad = bytearray(binary)
        bad[260] ^= 1
        # Change the entire ELF's expected SHA so that the per-function
        # digest, not just the whole-binary gate, is independently tested.
        profile["elf_sha256"] = sha256(bad)
        with self.assertRaisesRegex(ValueError, "function fingerprint"):
            attest(bytes(bad), profile)

    def test_library_hash_refuses_unknown_build(self):
        binary, profile = fixture()
        with self.assertRaisesRegex(ValueError, "ELF SHA-256"):
            attest(binary + b"changed", profile)

    def test_non_arm64_refused(self):
        binary, profile = fixture()
        bad = bytearray(binary)
        struct.pack_into("<H", bad, 18, 62)
        profile["elf_sha256"] = sha256(bad)
        with self.assertRaisesRegex(ValueError, "AArch64"):
            attest(bytes(bad), profile)

    def test_unmapped_target_refused(self):
        binary, profile = fixture()
        profile["targets"][0]["vaddr"] = "0x800"
        with self.assertRaisesRegex(ValueError, "does not map"):
            attest(binary, profile)

    def test_short_signature_refused(self):
        binary, profile = fixture()
        profile["targets"][0]["size"] = 4
        with self.assertRaisesRegex(ValueError, "insufficient"):
            attest(binary, profile)

    def test_duplicate_target_refused(self):
        binary, profile = fixture()
        profile["targets"].append(dict(profile["targets"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate name"):
            attest(binary, profile)


if __name__ == "__main__":
    unittest.main()
