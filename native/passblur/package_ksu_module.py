#!/usr/bin/env python3
"""Package an exactly verified PassBlur patch for KernelSU + Hybrid Mount.

The checked-in manifest has no approved patch sites, so this deliberately
refuses to emit an installable ZIP until the native ABI work is complete.
"""

import argparse
import json
import os
from pathlib import Path
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from offline_patch import approved_sites, expected_patched
from verify_binary import sha256, verify


MODULE_ID = "liquiddock_passblur_native"
LIBRARY = "system/system_ext/lib64/libsurfaceflinger.so"


def module_files(original, patched, manifest):
    failures = verify(original, manifest)
    if failures:
        raise ValueError("original identity failed: " + "; ".join(failures))
    sites = approved_sites(manifest, original)
    expected = expected_patched(original, sites)
    expected_sha = manifest.get("patched_sha256")
    if not expected_sha or sha256(expected) != expected_sha:
        raise ValueError("approved patch plan lacks a valid patched_sha256")
    if patched != expected:
        raise ValueError("patched binary differs from the approved instruction plan")
    if patched == original:
        raise ValueError("patch plan does not change the library")

    original_sha = sha256(original)
    prop = (
        f"id={MODULE_ID}\n"
        "name=LiquidDock PassBlur Native\n"
        "version=1\n"
        "versionCode=1\n"
        "author=LiquidDock\n"
        "description=Exact-build PassBlur patch mounted via Hybrid Mount\n"
    )
    installer = f"""#!/system/bin/sh
# KernelSU sources this file after extracting the ZIP. Hybrid Mount owns mounts.
[ "$KSU" = true ] || abort "KernelSU is required"
[ "$ARCH" = arm64 ] || abort "AArch64 is required"
HM=/data/adb/modules/hybrid_mount
[ -f "$HM/module.prop" ] && [ ! -e "$HM/disable" ] || abort "Enable Hybrid Mount first"
grep -qx 'id=hybrid_mount' "$HM/module.prop" || abort "Unexpected metamodule"
grep -qx 'metamodule=1' "$HM/module.prop" || abort "Hybrid Mount is not a metamodule"
STATE=/data/adb/hybrid-mount/run/state.json
if [ -f "$STATE" ] && grep -Eq '"failed_stage"[[:space:]]*:[[:space:]]*"' "$STATE"; then
  abort "Hybrid Mount failed this boot; repair its config and reboot first"
fi
ORIGINAL=/system_ext/lib64/libsurfaceflinger.so
[ -f "$ORIGINAL" ] || abort "SurfaceFlinger library is missing"
ACTUAL=$(sha256sum "$ORIGINAL" | awk '{{print $1}}')
[ "$ACTUAL" = '{original_sha}' ] || abort "Original SurfaceFlinger SHA256 differs"
PAYLOAD="$MODPATH/{LIBRARY}"
[ -f "$PAYLOAD" ] || abort "Patched library missing from ZIP"
ACTUAL=$(sha256sum "$PAYLOAD" | awk '{{print $1}}')
[ "$ACTUAL" = '{expected_sha}' ] || abort "Patched library SHA256 differs"
ui_print "- Exact binary verified; Hybrid Mount will mount on next reboot"
"""
    return {
        "module.prop": prop.encode(),
        "customize.sh": installer.encode(),
        "original.sha256": (original_sha + "\n").encode(),
        "patched.sha256": (expected_sha + "\n").encode(),
        LIBRARY: patched,
    }


def write_zip(output, files):
    output = output.resolve()
    if output.exists():
        raise ValueError("output ZIP already exists; refusing to overwrite")
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".passblur-module-", suffix=".zip", dir=output.parent)
    try:
        with os.fdopen(fd, "wb"):
            pass
        with ZipFile(temporary, "w", ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in files.items():
                entry = ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
                entry.compress_type = ZIP_DEFLATED
                entry.create_system = 3
                entry.external_attr = (0o100755 if name.endswith(".sh") else 0o100644) << 16
                archive.writestr(entry, data, compress_type=ZIP_DEFLATED, compresslevel=9)
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--patched", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, default=Path(__file__).with_name("compatibility.json"))
    args = parser.parse_args()
    if len({args.original.resolve(), args.patched.resolve(), args.output.resolve()}) != 3:
        parser.error("original, patched and output must be separate files")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    files = module_files(args.original.read_bytes(), args.patched.read_bytes(), manifest)
    write_zip(args.output, files)
    print(f"PACKAGED: {args.output} SHA256={sha256(args.output.read_bytes())}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(f"FAIL: {error}") from None
