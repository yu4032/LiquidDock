#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import struct
from pathlib import Path

EXPECTED_SHA256 = "407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32"
CODE_RVA = 0x00A4F200
STATE_RVA = 0x00AB1000
STATE_END_RVA = STATE_RVA + 0x40 + 128 * 0x20

HOOKS = {
    0x0054EBA0: (0x00A4F200, 0xA9BA7BFD, "drawPassBlurInternal"),
    0x005591B0: (0x00A4F240, 0x6DB63BEF, "background closure operator"),
    0x00422BC0: (0x00A4F2BC, 0xD10283FF, "dequeuePassBlurBuffer"),
    0x00422130: (0x00A4F2F0, 0xD10183FF, "setTextureWin"),
}

PT_LOAD = 1
PF_X = 1
PF_W = 2
PF_R = 4


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encode_b(pc: int, target: int) -> int:
    delta = target - pc
    if delta & 3:
        raise ValueError(f"unaligned branch {pc:#x} -> {target:#x}")
    imm = delta >> 2
    if not -(1 << 25) <= imm < (1 << 25):
        raise ValueError(f"branch out of range {pc:#x} -> {target:#x}")
    return 0x14000000 | (imm & 0x03FFFFFF)


def parse_elf64_le(buf: bytearray):
    if buf[:4] != b"\x7fELF" or buf[4] != 2 or buf[5] != 1:
        raise ValueError("expected ELF64 little-endian")
    e_phoff = struct.unpack_from("<Q", buf, 0x20)[0]
    e_phentsize = struct.unpack_from("<H", buf, 0x36)[0]
    e_phnum = struct.unpack_from("<H", buf, 0x38)[0]
    if e_phentsize != 56:
        raise ValueError(f"unexpected program header size {e_phentsize}")
    out = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_flags = struct.unpack_from("<II", buf, off)
        p_offset, p_vaddr, _, p_filesz, p_memsz, p_align = struct.unpack_from(
            "<QQQQQQ", buf, off + 8
        )
        out.append(
            {
                "index": i,
                "off": off,
                "type": p_type,
                "flags": p_flags,
                "offset": p_offset,
                "vaddr": p_vaddr,
                "filesz": p_filesz,
                "memsz": p_memsz,
                "align": p_align,
            }
        )
    return out


def rva_to_file(loads, rva: int) -> int:
    for p in loads:
        if p["type"] == PT_LOAD and p["vaddr"] <= rva < p["vaddr"] + p["filesz"]:
            return p["offset"] + (rva - p["vaddr"])
    raise ValueError(f"RVA {rva:#x} is not file-backed by any PT_LOAD")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("code", type=Path, help="raw .text blob linked at 0xa4f200")
    ap.add_argument("output", type=Path)
    args = ap.parse_args()

    original = args.input.read_bytes()
    actual = sha256(original)
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"refusing unknown libsurfaceflinger.so: sha256={actual}")

    code = args.code.read_bytes()
    if not code or len(code) > 0xE00:
        raise SystemExit(f"unexpected patch code size: {len(code)}")

    buf = bytearray(original)
    ph = parse_elf64_le(buf)
    loads = [p for p in ph if p["type"] == PT_LOAD]

    rx = next(
        (
            p
            for p in loads
            if p["flags"] == (PF_R | PF_X) and p["vaddr"] == 0x2E8000
        ),
        None,
    )
    rw = next(
        (
            p
            for p in loads
            if p["flags"] == (PF_R | PF_W) and p["vaddr"] == 0xAA4000
        ),
        None,
    )
    if rx is None or rw is None:
        raise SystemExit("expected target PT_LOAD layout not found")

    old_rx_end = rx["vaddr"] + rx["filesz"]
    code_end = CODE_RVA + len(code)
    if old_rx_end != 0xA4F150:
        raise SystemExit(f"unexpected RX end: {old_rx_end:#x}")
    if not (old_rx_end <= CODE_RVA < code_end <= 0xA50000):
        raise SystemExit("patch code does not fit verified RX alignment gap")

    code_off = rx["offset"] + (CODE_RVA - rx["vaddr"])
    if any(buf[code_off : code_off + len(code)]):
        raise SystemExit("verified RX code cave is not zero-filled")
    buf[code_off : code_off + len(code)] = code

    new_rx_size = code_end - rx["vaddr"]
    struct.pack_into("<Q", buf, rx["off"] + 32, new_rx_size)
    struct.pack_into("<Q", buf, rx["off"] + 40, new_rx_size)

    if rw["vaddr"] + rw["memsz"] != 0xAB04F0:
        raise SystemExit(
            f"unexpected final RW end: {rw['vaddr'] + rw['memsz']:#x}"
        )
    if STATE_END_RVA > 0xAB4000:
        raise SystemExit("sidecar state exceeds reserved RW extension")
    struct.pack_into("<Q", buf, rw["off"] + 40, 0x10000)

    original_loads = parse_elf64_le(bytearray(original))
    for site, (target, expected_word, label) in HOOKS.items():
        off = rva_to_file(original_loads, site)
        word = struct.unpack_from("<I", original, off)[0]
        if word != expected_word:
            raise SystemExit(
                f"{label}: instruction mismatch at {site:#x}: {word:#010x}"
            )
        struct.pack_into("<I", buf, off, encode_b(site, target))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(buf)
    print(f"input_sha256={actual}")
    print(f"code_size={len(code)}")
    print(f"output_sha256={sha256(buf)}")
    for site, (target, _, label) in HOOKS.items():
        print(f"hook {label}: {site:#x} -> {target:#x}")
    print(f"state={STATE_RVA:#x}..{STATE_END_RVA:#x}")


if __name__ == "__main__":
    main()
