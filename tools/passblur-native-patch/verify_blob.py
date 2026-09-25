#!/usr/bin/env python3
import argparse
import struct
from pathlib import Path

CODE_BASE = 0xA4F200
EXPECTED_SIZE_MAX = 0xE00

EXPECTED_B = {
    0x03C: 0x54EBA4,
    0x08C: 0x5591B4,
    0x0EC: 0x422BC4,
    0x110: 0x422134,
}

EXPECTED_FIRST = {
    0x000: 0xA9BA7BFD,
    0x084: 0x6DB63BEF,
    0x0E8: 0xD10283FF,
    0x0F0: 0xD10183FF,
}


def decode_b(pc: int, word: int) -> int:
    if (word & 0x7C000000) != 0x14000000:
        raise AssertionError(f"not B/BL at {pc:#x}: {word:#010x}")
    if word & 0x80000000:
        raise AssertionError(f"unexpected BL at {pc:#x}: {word:#010x}")
    imm26 = word & 0x03FFFFFF
    if imm26 & (1 << 25):
        imm26 -= 1 << 26
    return pc + (imm26 << 2)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("blob", type=Path)
    args = ap.parse_args()

    data = args.blob.read_bytes()
    assert 0 < len(data) <= EXPECTED_SIZE_MAX, len(data)

    for off, target in EXPECTED_B.items():
        word = struct.unpack_from("<I", data, off)[0]
        actual = decode_b(CODE_BASE + off, word)
        assert actual == target, (
            hex(CODE_BASE + off),
            hex(actual),
            hex(target),
        )

    for off, expected in EXPECTED_FIRST.items():
        word = struct.unpack_from("<I", data, off)[0]
        assert word == expected, (hex(off), hex(word), hex(expected))

    print(f"patch blob verified: {len(data)} bytes")


if __name__ == "__main__":
    main()
