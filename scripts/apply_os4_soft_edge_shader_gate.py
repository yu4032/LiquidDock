from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1))


# 1) Portable Prismal params own the independent OS4 mode bit.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java",
    '''    public final float highlightWidth;\n    public final float os4EdgeWidthPx;\n''',
    '''    public final float highlightWidth;\n    public final boolean os4SoftEdgeEnabled;\n    public final float os4EdgeWidthPx;\n''')
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java",
    '''        highlightWidth = b.highlightWidth;\n        os4EdgeWidthPx = b.os4EdgeWidthPx;\n''',
    '''        highlightWidth = b.highlightWidth;\n        os4SoftEdgeEnabled = b.os4SoftEdgeEnabled;\n        os4EdgeWidthPx = b.os4EdgeWidthPx;\n''')
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java",
    '''        public float highlightWidth = 4f;\n        // OS4 runtime values were not recovered; these are bounded implementation defaults.\n''',
    '''        public float highlightWidth = 4f;\n        public boolean os4SoftEdgeEnabled = true;\n        // OS4 runtime values were not recovered; these are bounded implementation defaults.\n''')

# 2) App material carries the mode without altering saved legacy highlight choices.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''        final float brightness;\n        final float highlightWidth;\n        final float os4EdgeWidthPx;\n''',
    '''        final float brightness;\n        final float highlightWidth;\n        final boolean os4SoftEdgeEnabled;\n        final float os4EdgeWidthPx;\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                float brightness,\n                float highlightWidth,\n                float os4EdgeWidthPx,\n''',
    '''                float brightness,\n                float highlightWidth,\n                boolean os4SoftEdgeEnabled,\n                float os4EdgeWidthPx,\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''            this.brightness = brightness;\n            this.highlightWidth = highlightWidth;\n            this.os4EdgeWidthPx = os4EdgeWidthPx;\n''',
    '''            this.brightness = brightness;\n            this.highlightWidth = highlightWidth;\n            this.os4SoftEdgeEnabled = os4SoftEdgeEnabled;\n            this.os4EdgeWidthPx = os4EdgeWidthPx;\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                1.08f,\n                1f,\n                20f,\n''',
    '''                1.08f,\n                1f,\n                true,\n                20f,\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                glass.brightness,\n                glass.highlightWidth,\n                glass.os4EdgeWidthPx,\n''',
    '''                glass.brightness,\n                glass.highlightWidth,\n                glass.os4SoftEdgeEnabled,\n                glass.os4EdgeWidthPx,\n''')

# 3) Portable bridge propagates the bit exactly.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalAdapter.java",
    '''        b.brightness = p.brightness;\n        b.highlightWidth = p.highlightWidth;\n        b.os4EdgeWidthPx = p.os4EdgeWidthPx;\n''',
    '''        b.brightness = p.brightness;\n        b.highlightWidth = p.highlightWidth;\n        b.os4SoftEdgeEnabled = p.os4SoftEdgeEnabled;\n        b.os4EdgeWidthPx = p.os4EdgeWidthPx;\n''')

# 4) Renderer uploads the mode independently from the nine legacy component gates.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java",
    '''        uniform4f("u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);\n        uniform1f("u_highlightWidth", p.highlightWidth);\n        uniform1f("u_os4EdgeWidthPx", p.os4EdgeWidthPx);\n''',
    '''        uniform4f("u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);\n        uniform1f("u_highlightWidth", p.highlightWidth);\n        uniform1f("u_os4SoftEdgeEnabled", p.os4SoftEdgeEnabled ? 1f : 0f);\n        uniform1f("u_os4EdgeWidthPx", p.os4EdgeWidthPx);\n''')

# 5) Shader mode gates only OS4 optical contributions. Legacy optics/highlight components remain available.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java",
    '''    private static final String OS4_EDGE_UNIFORMS = """\n            uniform float u_os4EdgeWidthPx;\n''',
    '''    private static final String OS4_EDGE_UNIFORMS = """\n            uniform float u_os4SoftEdgeEnabled;\n            uniform float u_os4EdgeWidthPx;\n''')
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java",
    '''                        + "    // OS4 edge width controls the internal optical band, never output alpha.\\n"\n                        + "    float os4EdgePx = u_os4EdgeWidthPx > 0.0\\n"\n''',
    '''                        + "    // OS4 mode is independent from all nine legacy highlight component gates.\\n"\n                        + "    float os4Mode = step(0.5, u_os4SoftEdgeEnabled);\\n"\n                        + "    // OS4 edge width controls the internal optical band, never output alpha.\\n"\n                        + "    float os4EdgePx = u_os4EdgeWidthPx > 0.0\\n"\n''')
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java",
    '''                        + "    float sdfNormalBlend = os4EdgeBand(edgeDist, "\n                        + "max(edgePixelFootprint * 2.0, "\n                        + "clamp(minDim * 0.055 * opticalEdgeScale, 2.0, 12.0)), edgeAa);\\n"\n''',
    '''                        + "    float sdfNormalBlend = os4EdgeBand(edgeDist, "\n                        + "max(edgePixelFootprint * 2.0, "\n                        + "clamp(minDim * 0.055 * opticalEdgeScale, 2.0, 12.0)), edgeAa) * os4Mode;\\n"\n''')
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java",
    '''        return "float os4VolumeMask = os4EdgeBand(edgeDist, os4EdgePx, edgeAa);\\n"\n''',
    '''        return "float os4VolumeMask = os4EdgeBand(edgeDist, os4EdgePx, edgeAa) * os4Mode;\\n"\n''')

# 6) Remove the temporary coupling that injected legacy Lit/Opposite Rim gates into OS4 light.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java",
    '''        corrected = replaceExactlyOnce(corrected,\n                "* max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff;",\n                "* max(u_os4DirectionalIntensity, 0.0) * u_componentLitRim * os4MainFalloff;",\n                "OS4 main directional component");\n        corrected = replaceExactlyOnce(corrected,\n                "* max(u_os4DirectionalOppositeIntensity, 0.0) * os4OppositeFalloff;",\n                "* max(u_os4DirectionalOppositeIntensity, 0.0) * u_componentOppositeRim * os4OppositeFalloff;",\n                "OS4 opposite directional component");\n        return corrected;\n''',
    '''        return corrected;\n''')

print("OS4 soft-edge shader gate patch applied")
