#!/usr/bin/env python3
"""Apply/verify/restore an explicitly reviewed AArch64 instruction patch plan.

The checked-in compatibility manifest currently has no approved patch sites.
Consequently `apply` refuses to produce a modified library. This tool is an
offline guard, never an on-device patcher.
"""

import argparse
import json
import os
from pathlib import Path
import tempfile

from verify_binary import executable_segments, file_offset, sha256, verify


def approved_sites(manifest, original):
    sites = manifest.get("patch_sites", [])
    if not sites:
        raise ValueError("no approved patch sites; refusing to create a patched binary")
    segments = list(executable_segments(original))
    converted = []
    for site in sites:
        before = bytes.fromhex(site["before"])
        after = bytes.fromhex(site["after"])
        vaddr = int(site["elf_vaddr"], 16)
        if not before or len(before) != len(after) or len(before) % 4 or vaddr % 4:
            raise ValueError(f"invalid AArch64 instruction span: {site['name']}")
        offset = file_offset(segments, vaddr, len(before))
        if original[offset : offset + len(before)] != before:
            raise ValueError(f"target instruction bytes differ: {site['name']}")
        converted.append((offset, before, after))
    converted.sort()
    for left, right in zip(converted, converted[1:]):
        if left[0] + len(left[1]) > right[0]:
            raise ValueError("overlapping patch sites")
    return converted


def expected_patched(original, sites):
    patched = bytearray(original)
    for offset, _, after in sites:
        patched[offset : offset + len(after)] = after
    # Exhaustively check that every changed byte belongs to an approved span.
    allowed = set()
    for offset, before, _ in sites:
        allowed.update(range(offset, offset + len(before)))
    for offset, (old, new) in enumerate(zip(original, patched)):
        if old != new and offset not in allowed:
            raise ValueError(f"unrelated byte changed at 0x{offset:x}")
    if len(patched) != len(original):
        raise ValueError("patch changed ELF file length")
    return bytes(patched)


def atomic_write(destination, data):
    destination = destination.resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".passblur-", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("apply", "verify-patched", "restore"))
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--manifest", type=Path, default=Path(__file__).with_name("compatibility.json")
    )
    args = parser.parse_args()
    if args.original.resolve() == args.output.resolve():
        parser.error("output must differ from the byte-identical original/backup")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    original = args.original.read_bytes()
    failures = verify(original, manifest)
    if failures:
        raise ValueError("original identity failed: " + "; ".join(failures))
    if args.operation == "restore":
        atomic_write(args.output, original)
        print(f"RESTORED: {args.output} SHA256={sha256(original)}")
        return
    sites = approved_sites(manifest, original)
    expected = expected_patched(original, sites)
    expected_sha = manifest.get("patched_sha256")
    if not expected_sha:
        raise ValueError("approved patch sites require a pinned patched_sha256")
    if sha256(expected) != expected_sha:
        raise ValueError("patched SHA256 differs from approved manifest")
    if args.operation == "apply":
        if args.output.exists():
            raise ValueError("output already exists; refusing to overwrite")
        backup = args.output.with_name(args.output.name + ".original")
        if backup.resolve() == args.original.resolve() or backup.exists():
            raise ValueError("automatic original backup path already exists")
        atomic_write(backup, original)
        atomic_write(args.output, expected)
        print(
            f"PATCHED: {args.output} SHA256={sha256(expected)} sites={len(sites)} "
            f"original_backup={backup}"
        )
    else:
        actual = args.output.read_bytes()
        if actual != expected:
            raise ValueError("patched bytes differ or unrelated bytes changed")
        print(f"PASS: patched SHA256={sha256(actual)} sites={len(sites)}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(f"FAIL: {error}") from None
