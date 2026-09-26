#!/usr/bin/env python3
import argparse
import struct
from pathlib import Path


def read_cstr(data: bytes, offset: int) -> str:
    end = data.find(b"\0", offset)
    if end < 0:
        raise ValueError("unterminated section name")
    return data[offset:end].decode("ascii")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("object", type=Path)
    ap.add_argument("output", type=Path)
    args = ap.parse_args()

    data = args.object.read_bytes()
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise SystemExit("expected ELF64 little-endian relocatable object")

    e_type = struct.unpack_from("<H", data, 0x10)[0]
    if e_type != 1:
        raise SystemExit(f"expected ET_REL, got {e_type}")

    shoff = struct.unpack_from("<Q", data, 0x28)[0]
    shentsize = struct.unpack_from("<H", data, 0x3A)[0]
    shnum = struct.unpack_from("<H", data, 0x3C)[0]
    shstrndx = struct.unpack_from("<H", data, 0x3E)[0]
    if shentsize != 64 or shstrndx >= shnum:
        raise SystemExit("unexpected ELF section layout")

    sections = []
    for i in range(shnum):
        off = shoff + i * shentsize
        sh_name = struct.unpack_from("<I", data, off)[0]
        sh_type = struct.unpack_from("<I", data, off + 4)[0]
        sh_offset = struct.unpack_from("<Q", data, off + 24)[0]
        sh_size = struct.unpack_from("<Q", data, off + 32)[0]
        sections.append((sh_name, sh_type, sh_offset, sh_size))

    _, _, names_off, names_size = sections[shstrndx]
    names = data[names_off : names_off + names_size]

    named = {}
    for sh_name, sh_type, sh_offset, sh_size in sections:
        name = read_cstr(names, sh_name)
        named[name] = (sh_type, sh_offset, sh_size)

    rela = named.get(".rela.text")
    if rela is not None and rela[2] != 0:
        raise SystemExit(
            f".text still has {rela[2]} bytes of unresolved relocations"
        )

    text = named.get(".text")
    if text is None:
        raise SystemExit("missing .text")
    _, text_off, text_size = text
    if text_size == 0:
        raise SystemExit("empty .text")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data[text_off : text_off + text_size])
    print(f"extracted .text: {text_size} bytes")


if __name__ == "__main__":
    main()
