import copy
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from offline_patch import approved_sites, expected_patched
from package_ksu_module import LIBRARY, module_files, write_zip
from test_binary_guards import fixture
from verify_binary import sha256


class KernelSUModuleTest(unittest.TestCase):
    def test_unapproved_manifest_cannot_emit_module(self):
        original, manifest = fixture()
        manifest["patch_sites"] = []
        with self.assertRaisesRegex(ValueError, "no approved patch sites"):
            module_files(original, original, manifest)

    def test_exact_payload_and_hybrid_layout(self):
        original, manifest = fixture()
        patched = expected_patched(original, approved_sites(manifest, original))
        manifest["patched_sha256"] = sha256(patched)
        files = module_files(original, patched, manifest)
        self.assertEqual(patched, files[LIBRARY])
        self.assertNotIn("system_ext/lib64/libsurfaceflinger.so", files)
        self.assertIn(b"Hybrid Mount", files["module.prop"])
        self.assertIn(sha256(original).encode(), files["customize.sh"])
        self.assertIn(sha256(patched).encode(), files["customize.sh"])
        self.assertIn(b"failed_stage", files["customize.sh"])
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "module.zip"
            write_zip(output, files)
            with ZipFile(output) as archive:
                self.assertEqual(set(files), set(archive.namelist()))
                for name, data in files.items():
                    self.assertEqual(data, archive.read(name))
                self.assertNotIn(b"\r", archive.read("module.prop"))
                self.assertNotIn(b"\r", archive.read("customize.sh"))
                self.assertEqual(3, archive.getinfo("customize.sh").create_system)
            with self.assertRaisesRegex(ValueError, "already exists"):
                write_zip(output, files)

    def test_tampering_and_wrong_original_are_rejected(self):
        original, manifest = fixture()
        patched = expected_patched(original, approved_sites(manifest, original))
        manifest["patched_sha256"] = sha256(patched)
        bad = bytearray(patched)
        bad[0x140] ^= 1
        with self.assertRaisesRegex(ValueError, "differs from the approved"):
            module_files(original, bytes(bad), manifest)
        with self.assertRaisesRegex(ValueError, "original identity failed"):
            module_files(original[:-1], patched, manifest)
        wrong = copy.deepcopy(manifest)
        wrong["patched_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "patched_sha256"):
            module_files(original, patched, wrong)


if __name__ == "__main__":
    unittest.main()
