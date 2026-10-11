#!/usr/bin/env python3
"""Offline Prismal GUI chrome baker, Python 3 stdlib only.

Bakes the FINAL fixed-background appearance rather than a raw specular alpha map.
The original shader (PrismalAGSL v1.0.4) calculates Default specular using
abs(dot(gradSdRoundRect, vec2(cos(45°), sin(45°)))) with white alpha 0.5.
Crucially, PrismalSpecularModifier then applies BlendMode.Plus. Over the
near-white screenshot-matched Cell background, this saturates most of the thin
highlight to WHITE; normal differences show mostly in the edge falloff.

This baker approximates the specular stroke/blur spatially OFFLINE.
It is *not* an AGSL renderer, and a continuous-curvature superellipse is an
approximation to PrismalRoundedRectangle's Bezier outline. Active sliders and
toggles still use Prismal's real dynamic Ambient shader. At rest their original
onDrawSurface is opaque white and Ambient alpha is zero.

Source of truth:
  Prismal/src/main/assets/agsl/{default_specular,rounded_rect_sdf}.agsl
  Prismal/src/main/java/com/styropyr0/prismal/specular/PrismalSpecularModifier.kt
  https://github.com/styropyr0/PrismalAGSL/tree/v1.0.4

Run: python3 scripts/bake_gui_materials.py
Output: 4-bit paletted AAPT2-compatible Android nine-patches in res/drawable*-xxhdpi.
"""
from pathlib import Path
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/res"
SCALE = 3  # xxhdpi pixels per dp
SHAPES = {
    "card": (80, 64, 24),
    "pill": (52, 36, 18),
    "thumb": (40, 24, 12),
    "danger": (52, 36, 18),
}
BASE = {
    "light": {
        "card": (244, 244, 252),  # sampled reference: #F4F4FC
        "pill": (242, 242, 250),
        "thumb": (255, 255, 255),
        "danger": (215, 51, 51),
    },
    "dark": {
        "card": (52, 59, 70),
        "pill": (69, 78, 91),
        "thumb": (255, 255, 255),
        "danger": (215, 51, 51),
    },
}


def png_chunk(kind, payload):
    data = kind.encode("ascii") + payload
    return struct.pack(">I", len(payload)) + data + struct.pack(
        ">I", zlib.crc32(data) & 0xffffffff
    )


def normal_dot(x, y, w, h, r):
    # Prismal specular shader uses gradRadius=min(shapeRadius*1.5,halfSize),
    # distinct from the continuous-shape radius used for clipping.
    radius = min(1.5 * r, w / 2, h / 2)
    qx = abs(x) - (w / 2 - radius)
    qy = abs(y) - (h / 2 - radius)
    vx = max(qx, 0)
    vy = max(qy, 0)
    if qx >= 0 or qy >= 0:
        length = math.hypot(vx, vy)
        gx = math.copysign(vx / length if length else 0, x)
        gy = math.copysign(vy / length if length else 0, y)
    else:
        horizontal = 1.0 if qy <= qx else 0.0
        gx = math.copysign(horizontal, x)
        gy = math.copysign(1 - horizontal, y)
    return (gx + gy) / math.sqrt(2.0)


def clip_distance(x, y, w, h, r):
    # Superellipse approximates the upstream Continuous Bezier contour rather
    # than substituting a hard-corner rectangle for the Cell silhouette.
    dx = abs(x) - (w / 2 - r)
    dy = abs(y) - (h / 2 - r)
    if dx > 0 and dy > 0:
        return (dx ** 4 + dy ** 4) ** 0.25 - r
    if dx > 0:
        return dx - r
    if dy > 0:
        return dy - r
    return max(dx, dy) - r


def bake(kind, theme):
    width_dp, height_dp, radius_dp = SHAPES[kind]
    width, height, radius = (p * SCALE for p in (width_dp, height_dp, radius_dp))
    columns, rows = width + 2, height + 2
    fill = BASE[theme][kind]
    headroom = min(128, 255 - min(fill))
    palette = [(0, 0, 0, 0), (0, 0, 0, 255), (*fill, 255)]
    for level in range(1, 13):
        palette.append((
            *(min(255, round(c + headroom * level / 12)) for c in fill),
            255,
        ))
    palette.append((*fill, 145))  # antialiased contour fringe
    assert len(palette) == 16
    pixels = bytearray(columns * rows)
    for y in range(height):
        for x in range(width):
            cx, cy = x + .5 - width / 2, y + .5 - height / 2
            signed = clip_distance(cx, cy, width, height, radius)
            if signed > .5:
                continue
            if signed > -.2:
                index = 15
            elif kind == "thumb":
                # Upstream Ambient is alpha=pressProgress; at rest it is ZERO.
                # Upstream onDrawSurface fills the idle thumb with opaque white.
                index = 2
            else:
                d = abs(normal_dot(cx, cy, width, height, radius))
                # Clipped 2*ceil(0.5dp) stroke with ~0.25dp soft falloff.
                band = 1 / (1 + math.exp((-signed - 1.7) / .55))
                alpha = .5 * (.06 + .94 * d) * band
                added = 255 * alpha  # BlendMode.Plus, NOT SrcOver white
                intensity = max(min(255, c + added) - c for c in fill)
                level = max(0, min(12, round(intensity / headroom * 12)))
                index = 2 + level if level else 2
            pixels[(y + 1) * columns + (x + 1)] = index

    # Black 1-pixel nine-patch guides: central stretch avoids corner distortion.
    for x in range(width // 2 - 9, width // 2 + 10):
        pixels[x + 1] = 1
    yspan = min(9, max(2, round((height - 2 * radius) / 2)))
    for y in range(height // 2 - yspan, height // 2 + yspan + 1):
        pixels[(y + 1) * columns] = 1
    for x in range(2, width):
        pixels[(rows - 1) * columns + x] = 1
    for y in range(2, height):
        pixels[y * columns + columns - 1] = 1

    stride = (columns + 1) // 2
    scanlines = bytearray()
    for y in range(rows):
        scanlines.append(0)  # no PNG row filter
        for x in range(0, columns, 2):
            hi = pixels[y * columns + x]
            lo = pixels[y * columns + x + 1] if x + 1 < columns else 0
            scanlines.append((hi << 4) | lo)
    ihdr = struct.pack(">IIBBBBB", columns, rows, 4, 3, 0, 0, 0)
    out = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk("IHDR", ihdr)
        + png_chunk("PLTE", bytes(c for p in palette for c in p[:3]))
        + png_chunk("tRNS", bytes(p[3] for p in palette))
        + png_chunk("IDAT", zlib.compress(bytes(scanlines), level=0))
        + png_chunk("IEND", b"")
    )
    folder = ROOT / ("drawable-xxhdpi" if theme == "light" else "drawable-night-xxhdpi")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / ("gui_material_" + kind + ".9.png")).write_bytes(out)


if __name__ == "__main__":
    for theme in BASE:
        for kind in SHAPES:
            bake(kind, theme)
    print("Baked 8 static Plus-composited Prismal GUI materials.")
