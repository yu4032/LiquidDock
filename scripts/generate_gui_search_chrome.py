#!/usr/bin/env python3
"""Compile-time Prismal-inspired 48dp capsule highlight and shadow raster.

Pure stdlib; reproducible PNG generation, no runtime blur or shader.
From the repository root:
    python3 scripts/generate_gui_search_chrome.py

The source image is a 104x48dp pill plus 6dp overscan all around, at
3 source pixels/dp. During layout, only the straight 8dp center strip
is stretched: curved ends and outer shadows are never distorted.
"""
from math import exp, hypot, copysign, sqrt
from pathlib import Path
import struct
import zlib

SCALE = 3
FIELD_WIDTH_DP = 104
FIELD_HEIGHT_DP = 48
INSET_DP = 6
RADIUS_DP = FIELD_HEIGHT_DP / 2
OUT = Path("src/main/res/drawable-nodpi/gui_search_prismal_chrome.png")


def signed_distance_normal(x, y, width, height, radius):
    """Rounded-rectangle SDF and 2-D normal specialized for a Prismal pill."""
    cx, cy = width / 2, height / 2
    px, py = x - cx, y - cy
    rx = abs(px) - (width / 2 - radius)
    ry = abs(py) - (height / 2 - radius)
    ox, oy = max(rx, 0), max(ry, 0)
    d = hypot(ox, oy) + min(max(rx, ry), 0) - radius
    if ox or oy:
        n = hypot(ox, oy)
        nx, ny = ox / n, oy / n
    elif rx > ry:
        nx, ny = 1, 0
    else:
        nx, ny = 0, 1
    return d, copysign(nx, px), copysign(ny, py)


def generate():
    width = (FIELD_WIDTH_DP + 2 * INSET_DP) * SCALE
    height = (FIELD_HEIGHT_DP + 2 * INSET_DP) * SCALE
    raw = bytearray()
    for iy in range(height):
        raw.append(0)  # PNG scanline: no filter
        for ix in range(width):
            px = (ix + .5) / SCALE - INSET_DP
            py = (iy + .5) / SCALE - INSET_DP
            distance, nx, ny = signed_distance_normal(
                px, py, FIELD_WIDTH_DP, FIELD_HEIGHT_DP, RADIUS_DP)
            # Normal-lit directional rim, calculated only at generation time.
            lambert = abs((nx + ny) / sqrt(2))
            inward = max(0., 1. - abs(distance + .55) / 1.35)
            rim = inward * (.32 + .68 * lambert) * .68
            # Baked soft outer drop shadow, 1.7dp below the geometry.
            sdist, _, _ = signed_distance_normal(
                px, py - 1.7, FIELD_WIDTH_DP, FIELD_HEIGHT_DP, RADIUS_DP)
            shadow = (0.24 * exp(-max(0., sdist) ** 2 / (2 * 2.2 ** 2))
                      if distance >= 0 and sdist > -1 else 0.)
            if rim > shadow * 1.5:
                raw.append(min(16, max(1, round(rim * 16))))
            elif shadow > 0.01:
                raw.append(16 + min(16, max(1, round(shadow * 16))))
            else:
                raw.append(0)

    def chunk(name, payload):
        return (struct.pack(">I", len(payload)) + name + payload
                + struct.pack(">I", zlib.crc32(name + payload) & 0xffffffff))

    palette = bytearray((255, 255, 255))
    palette.extend((255, 255, 255) * 16)
    palette.extend((5, 8, 15) * 16)
    alpha = bytearray([0] + [min(255, round(i * 255 / 16)) for i in range(1, 17)] * 2)
    png = (b"\x89PNG\r\n\x1a\n" +
           chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 3, 0, 0, 0)) +
           chunk(b"PLTE", palette) + chunk(b"tRNS", alpha) +
           chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png)
    print(f"Baked GUI search chrome: {width}x{height}, {len(png)} bytes -> {OUT}")


if __name__ == "__main__":
    generate()
