#!/usr/bin/env python3
"""Verify the exact SurfaceFlinger build used by the PassBlur analysis.

This intentionally does not modify a system library.  An ELF virtual address is
translated through PT_LOAD rather than assumed to equal a file offset.
"""

import argparse
import hashlib
import json
from pathlib import Path
import struct


PT_LOAD = 1
PF_X = 1


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def executable_segments(data):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("expected little-endian ELF64")
    if struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError("expected AArch64 (EM_AARCH64=183)")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize = struct.unpack_from("<H", data, 54)[0]
    phnum = struct.unpack_from("<H", data, 56)[0]
    if phentsize < 56 or phoff + phentsize * phnum > len(data):
        raise ValueError("invalid ELF program header table")
    for index in range(phnum):
        at = phoff + index * phentsize
        kind, flags, offset, vaddr, _, filesz, _, _ = struct.unpack_from(
            "<IIQQQQQQ", data, at
        )
        if kind == PT_LOAD and flags & PF_X:
            if offset + filesz > len(data):
                raise ValueError("executable segment exceeds file")
            yield (vaddr, filesz, offset)


def file_offset(segments, vaddr, size):
    for start, length, offset in segments:
        if start <= vaddr and vaddr + size <= start + length:
            return offset + vaddr - start
    raise ValueError(f"0x{vaddr:x}+{size} is not inside executable PT_LOAD")


def verify(data, manifest):
    failures = []
    if len(data) != manifest["binary"]["size"]:
        failures.append("binary size differs")
    actual_sha = sha256(data)
    if actual_sha != manifest["binary"]["sha256"]:
        failures.append(f"binary SHA256 differs: {actual_sha}")
    try:
        segments = list(executable_segments(data))
        if not segments:
            failures.append("no executable PT_LOAD")
        for function in manifest["functions"]:
            vaddr = int(function["elf_vaddr"], 16)
            size = function["size"]
            offset = file_offset(segments, vaddr, size)
            actual = data[offset : offset + size]
            if sha256(actual) != function["sha256"]:
                failures.append(f"function bytes differ: {function['name']}")
            if not actual.startswith(bytes.fromhex(function["entry_bytes"])):
                failures.append(f"entry bytes differ: {function['name']}")
            if int(function["ghidra_address"], 16) - vaddr != manifest["ghidra_image_base"]:
                failures.append(f"address-base mismatch: {function['name']}")
    except (ValueError, KeyError, struct.error) as exc:
        failures.append(str(exc))
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument(
        "--manifest", type=Path, default=Path(__file__).with_name("compatibility.json")
    )
    args = parser.parse_args()
    data = args.binary.read_bytes()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    failures = verify(data, manifest)
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        raise SystemExit(1)
    print(
        f"PASS: {args.binary} SHA256={sha256(data)} "
        f"functions={len(manifest['functions'])}"
    )


if __name__ == "__main__":
    main()
