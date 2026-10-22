#!/usr/bin/env python3
"""Offline ARM64 ELF function attestation for the independently built OS4 port.

This validates disk bytes, NOT runtime mappings or Dart ABI. Fail closed on
unknown builds; never promote a success here directly to permission to hook.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

ELF_HEADER = struct.Struct("<HHIQQQIHHHHHH")
PROGRAM_HEADER = struct.Struct("<IIQQQQQQ")
HASH = re.compile(r"[0-9a-f]{64}\Z")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _digest(value: object, name: str) -> str:
    if not isinstance(value, str) or HASH.fullmatch(value) is None:
        raise ValueError(f"{name}: expected 64 lowercase hex digits")
    return value


def _integer(value: object, name: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{name}: invalid integer")
    if isinstance(value, int):
        result = value
    elif isinstance(value, str) and re.fullmatch(r"0x[0-9a-fA-F]+|[0-9]+", value):
        result = int(value, 0) if value.startswith("0x") else int(value, 10)
    else:
        raise ValueError(f"{name}: invalid integer")
    if result < 0 or result > (1 << 64) - 1:
        raise ValueError(f"{name}: out of range")
    return result


def load_segments(binary: bytes) -> list[tuple[int, int, int, int]]:
    if len(binary) < 64 or binary[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError("not a little-endian ELF64 binary")
    if ELF_HEADER.size + 16 != 64:
        raise RuntimeError("incorrect ELF structure")
    e = ELF_HEADER.unpack_from(binary, 16)
    elf_type, machine, version = e[:3]
    phoff, ehsize, phentsize, phnum = e[4], e[7], e[8], e[9]
    if elf_type != 3 or machine != 183 or version != 1:
        raise ValueError("expected AArch64 ET_DYN ELF")
    if ehsize != 64 or phentsize != PROGRAM_HEADER.size or not 1 <= phnum <= 256:
        raise ValueError("unsupported ELF program header table")
    if phoff < 64 or phoff + phnum * phentsize > len(binary):
        raise ValueError("truncated ELF program headers")
    result: list[tuple[int, int, int, int]] = []
    for i in range(phnum):
        p = PROGRAM_HEADER.unpack_from(binary, phoff + i * phentsize)
        p_type, flags, offset, vaddr, _, filesz, memsz, _ = p
        if p_type != 1:
            continue
        if filesz > memsz or offset + filesz > len(binary):
            raise ValueError(f"PT_LOAD {i}: invalid file extent")
        if vaddr + memsz > (1 << 64):
            raise ValueError(f"PT_LOAD {i}: address overflow")
        result.append((flags, vaddr, offset, filesz))
    if not result or not any(flags & 1 for flags, *_ in result):
        raise ValueError("missing executable PT_LOAD")
    return result


def attest(binary: bytes, profile: dict) -> int:
    if not isinstance(profile, dict):
        raise ValueError("profile must be an object")
    if sha256(binary) != _digest(profile.get("elf_sha256"), "elf_sha256"):
        raise ValueError("ELF SHA-256 mismatch")
    segments = load_segments(binary)
    targets = profile.get("targets")
    if not isinstance(targets, list) or not targets:
        raise ValueError("at least one function target is required")
    names: set[str] = set()
    for index, target in enumerate(targets):
        if not isinstance(target, dict):
            raise ValueError(f"target {index}: not an object")
        name = target.get("name")
        if not isinstance(name, str) or not name or name in names:
            raise ValueError(f"target {index}: invalid/duplicate name")
        names.add(name)
        vaddr = _integer(target.get("vaddr"), name + ".vaddr")
        size = _integer(target.get("size"), name + ".size")
        if size < 32 or size > 1024 * 1024:
            raise ValueError(f"{name}: insufficient or oversized function fingerprint")
        expected = _digest(target.get("sha256"), name + ".sha256")
        candidates = [
            (offset, segment_vaddr)
            for flags, segment_vaddr, offset, filesz in segments
            if flags & 1 and vaddr >= segment_vaddr
            and vaddr - segment_vaddr + size <= filesz
        ]
        if len(candidates) != 1:
            raise ValueError(f"{name}: target does not map uniquely into executable file bytes")
        offset, segment_vaddr = candidates[0]
        start = offset + vaddr - segment_vaddr
        if sha256(binary[start:start + size]) != expected:
            raise ValueError(f"{name}: complete function fingerprint mismatch")
    return len(targets)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf", type=Path, help="locally extracted target libapp.so")
    parser.add_argument("profile", type=Path, help="explicit independently verified JSON profile")
    args = parser.parse_args()
    count = attest(args.elf.read_bytes(), json.loads(args.profile.read_text()))
    print(f"PASS: disk ELF SHA-256 and {count} complete function bodies match")
    print("NOTE: runtime mappings, trampoline ABI and device behavior are NOT verified")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"REFUSED: {exc}")
