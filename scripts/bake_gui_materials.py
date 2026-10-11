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
Output: 8-bit RGBA Android nine-patches in res/drawable*-xxhdpi.\nIdle thumbs and danger buttons intentionally use simple XML shape resources.
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
    assert kind in ("card", "pill"), (
        "Idle thumbs use opaque-white XML; destructive actions use red XML."
    )
    width_dp, height_dp, radius_dp = SHAPES[kind]
    width, height, radius = (p * SCALE for p in (width_dp, height_dp, radius_dp))
    columns, rows = width + 2, height + 2
    fill = BASE[theme][kind]
    pixels = bytearray(columns * rows * 4)

    def put(x, y, red, green, blue, alpha):
        start = (y * columns + x) * 4
        pixels[start:start + 4] = bytes((red, green, blue, alpha))

    for y in range(height):
        for x in range(width):
            cx, cy = x + .5 - width / 2, y + .5 - height / 2
            signed = clip_distance(cx, cy, width, height, radius)
            coverage = max(0, min(1, .5 - signed))
            if coverage <= 0:
                continue
            d = abs(normal_dot(cx, cy, width, height, radius))
            # Approximate native 2*ceil(0.5dp) stroke, clipped at shape border;
            # slight Gaussian-like softness inside and the smaller adjacent lobe.
            band = 1 / (1 + math.exp((-signed - 1.7) / .55))
            plus = 255 * .5 * (.06 + .94 * d) * band
            rgb = tuple(min(255, round(c + plus)) for c in fill)
            put(x + 1, y + 1, *rgb, round(coverage * 255))

    # 1px native 9-patch guide border. Keep stretch bars away from the corners.
    for x in range(width // 2 - 9, width // 2 + 10):
        put(x + 1, 0, 0, 0, 0, 255)
    yspan = min(9, max(2, round((height - 2 * radius) / 2)))
    for y in range(height // 2 - yspan, height // 2 + yspan + 1):
        put(0, y + 1, 0, 0, 0, 255)
    for x in range(2, width):
        put(x, rows - 1, 0, 0, 0, 255)
    for y in range(2, height):
        put(columns - 1, y, 0, 0, 0, 255)

    # AAPT2 did not reliably compile the earlier paletted 9-patches with a
    # mostly-opaque material. Use Android's standard truecolor RGBA PNG format.
    scanlines = bytearray()
    row_size = columns * 4
    for y in range(rows):
        scanlines.append(0)
        scanlines.extend(pixels[y * row_size:(y + 1) * row_size])
    ihdr = struct.pack(">IIBBBBB", columns, rows, 8, 6, 0, 0, 0)
    out = (
        b"\\x89PNG\\r\\n\\x1a\\n"
        + png_chunk("IHDR", ihdr)
        + png_chunk("IDAT", zlib.compress(bytes(scanlines), level=0))
        + png_chunk("IEND", b"")
    )
    folder = ROOT / ("drawable-xxhdpi" if theme == "light" else "drawable-night-xxhdpi")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / ("gui_material_" + kind + ".9.png")).write_bytes(out)


if __name__ == "__main__":
    for theme in BASE:
        for kind in ("card", "pill"):
            bake(kind, theme)
    print("Baked 4 RGBA Plus-composited Card/Pill Prismal GUI materials.")
