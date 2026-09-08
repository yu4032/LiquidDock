from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


# 1) Persisted schema + export/import registration.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Integer> PRISMAL_PLAIN_HIGHLIGHT = integer(
                "liquid_prismal_plain_highlight", 8, 8, 8, 0, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PRISMAL_LIGHT_DIR_X = integer(
''',
    '''        public static final ConfigKey<Integer> PRISMAL_PLAIN_HIGHLIGHT = integer(
                "liquid_prismal_plain_highlight", 8, 8, 8, 0, 100, ConfigKey.ExportMode.ALWAYS);
        // OS4 background-driven edge controls. Pixel distances are direct logical output pixels;
        // strength/range values use x100 storage like the surrounding Prismal controls.
        public static final ConfigKey<Integer> OS4_EDGE_WIDTH_PX = integer(
                "liquid_os4_edge_width_px", 20, 20, 20, 4, 64, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_REFLECT_OFFSET_PX = integer(
                "liquid_os4_reflect_offset_px", 10, 10, 10, 0, 40, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_REFLECTION_STRENGTH = integer(
                "liquid_os4_reflection_strength", 28, 28, 28, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_REFLECTION_LIGHTEN = integer(
                "liquid_os4_reflection_lighten", 16, 16, 16, 0, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_DIRECTIONAL_ANGLE_RANGE = integer(
                "liquid_os4_directional_angle_range", 52, 52, 52, 5, 150, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_DIRECTIONAL_INTENSITY = integer(
                "liquid_os4_directional_intensity", 42, 42, 42, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> OS4_DIRECTIONAL_OPPOSITE_INTENSITY = integer(
                "liquid_os4_directional_opposite_intensity", 14, 14, 14, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PRISMAL_LIGHT_DIR_X = integer(
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''                Glass.PRISMAL_DISPERSION_R, Glass.PRISMAL_DISPERSION_B,
                Glass.PRISMAL_VIBRANCY, Glass.PRISMAL_PLAIN_HIGHLIGHT,
                Glass.PRISMAL_LIGHT_DIR_X, Glass.PRISMAL_LIGHT_DIR_Y,
''',
    '''                Glass.PRISMAL_DISPERSION_R, Glass.PRISMAL_DISPERSION_B,
                Glass.PRISMAL_VIBRANCY, Glass.PRISMAL_PLAIN_HIGHLIGHT,
                Glass.OS4_EDGE_WIDTH_PX, Glass.OS4_REFLECT_OFFSET_PX,
                Glass.OS4_REFLECTION_STRENGTH, Glass.OS4_REFLECTION_LIGHTEN,
                Glass.OS4_DIRECTIONAL_ANGLE_RANGE, Glass.OS4_DIRECTIONAL_INTENSITY,
                Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
                Glass.PRISMAL_LIGHT_DIR_X, Glass.PRISMAL_LIGHT_DIR_Y,
''')

# 2) Typed runtime configuration.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''                prismalDispersionR, prismalDispersionB, prismalVibrancy, prismalPlainHighlight,
                prismalLightDirX, prismalLightDirY, prismalShadowSoftness, prismalTransmittance,
''',
    '''                prismalDispersionR, prismalDispersionB, prismalVibrancy, prismalPlainHighlight,
                os4EdgeWidthPx, os4ReflectOffsetPx, os4ReflectionStrength, os4ReflectionLighten,
                os4DirectionalAngleRange, os4DirectionalIntensity,
                os4DirectionalOppositeIntensity,
                prismalLightDirX, prismalLightDirY, prismalShadowSoftness, prismalTransmittance,
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            prismalPlainHighlight = c.i(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.runtimeFallback()) / 100f;
            prismalLightDirX = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
''',
    '''            prismalPlainHighlight = c.i(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.runtimeFallback()) / 100f;
            os4EdgeWidthPx = clamp(c.i(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.name(),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.runtimeFallback()),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.minInt(),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.maxInt());
            os4ReflectOffsetPx = clamp(c.i(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.name(),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.minInt(),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.maxInt());
            os4ReflectionStrength = clamp(c.i(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.minInt(),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.maxInt()) / 100f;
            os4ReflectionLighten = clamp(c.i(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.minInt(),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.maxInt()) / 100f;
            os4DirectionalAngleRange = clamp(c.i(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.maxInt()) / 100f;
            os4DirectionalIntensity = clamp(c.i(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.maxInt()) / 100f;
            os4DirectionalOppositeIntensity = clamp(c.i(
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.maxInt()) / 100f;
            prismalLightDirX = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
''')

# 3) Existing MIUIX material bridge owns runtime unit normalization and sampling reach.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''        final float highlightWidth;
        final float lightDirX;
''',
    '''        final float highlightWidth;
        final float os4EdgeWidthPx;
        final float os4ReflectOffsetPx;
        final float os4ReflectionStrength;
        final float os4ReflectionLighten;
        final float os4DirectionalAngleRange;
        final float os4DirectionalIntensity;
        final float os4DirectionalOppositeIntensity;
        final float lightDirX;
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                float highlightWidth,
                float lightDirX,
''',
    '''                float highlightWidth,
                float os4EdgeWidthPx,
                float os4ReflectOffsetPx,
                float os4ReflectionStrength,
                float os4ReflectionLighten,
                float os4DirectionalAngleRange,
                float os4DirectionalIntensity,
                float os4DirectionalOppositeIntensity,
                float lightDirX,
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''            this.highlightWidth = highlightWidth;
            this.lightDirX = lightDirX;
''',
    '''            this.highlightWidth = highlightWidth;
            this.os4EdgeWidthPx = os4EdgeWidthPx;
            this.os4ReflectOffsetPx = os4ReflectOffsetPx;
            this.os4ReflectionStrength = os4ReflectionStrength;
            this.os4ReflectionLighten = os4ReflectionLighten;
            this.os4DirectionalAngleRange = os4DirectionalAngleRange;
            this.os4DirectionalIntensity = os4DirectionalIntensity;
            this.os4DirectionalOppositeIntensity = os4DirectionalOppositeIntensity;
            this.lightDirX = lightDirX;
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                1.08f,
                1f,
                -0.5f,
                -0.8f,
''',
    '''                1.08f,
                1f,
                20f,
                10f,
                0.28f,
                0.16f,
                0.52f,
                0.42f,
                0.14f,
                -0.5f,
                -0.8f,
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''                glass.prismalPlainHighlight,
                glass.brightness,
                glass.highlightWidth,
                glass.prismalLightDirX,
''',
    '''                glass.prismalPlainHighlight,
                glass.brightness,
                glass.highlightWidth,
                glass.os4EdgeWidthPx,
                glass.os4ReflectOffsetPx,
                glass.os4ReflectionStrength,
                glass.os4ReflectionLighten,
                glass.os4DirectionalAngleRange,
                glass.os4DirectionalIntensity,
                glass.os4DirectionalOppositeIntensity,
                glass.prismalLightDirX,
''')

replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java",
    '''        float snell = Math.abs(p.thicknessPx) * 0.85f * Math.abs(p.displacementScale)
                * 1.18f * pxNorm;
        float modernBulge = axis * (0.014f + 0.01f * clamp(p.liquidDome, 0f, 2f)) * pxNorm;
        float modernBase = lens + parallax + snell + modernBulge;

        float baseReach = modernBase;

        float dispersion = Math.max(Math.abs(p.dispersionR), Math.abs(p.dispersionB));
        float chromatic = Math.abs(p.chromaticAberration) * 0.0018f
                * dispersion * pxNorm * axis;
        float reflection = 56f * pxNorm;
''',
    '''        float snell = Math.abs(p.thicknessPx) * 0.85f * Math.abs(p.displacementScale)
                * 1.18f * pxNorm;
        float os4EffectiveThickness = Math.max(
                Math.abs(p.thicknessPx), Math.max(0f, p.os4EdgeWidthPx) + 6f);
        float os4Volume = os4EffectiveThickness * 2f * Math.abs(p.displacementScale)
                * 1.18f * pxNorm;
        float modernBulge = axis * (0.014f + 0.01f * clamp(p.liquidDome, 0f, 2f)) * pxNorm;
        float modernBase = lens + parallax + snell + os4Volume + modernBulge;

        float baseReach = modernBase;

        float dispersion = Math.max(Math.abs(p.dispersionR), Math.abs(p.dispersionB));
        float chromatic = Math.abs(p.chromaticAberration) * 0.0018f
                * dispersion * pxNorm * axis;
        float reflection = Math.max(56f * pxNorm, Math.abs(p.os4ReflectOffsetPx) * 2f);
''')

# 4) Portable Prismal bridge.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalAdapter.java",
    '''        b.highlightWidth = p.highlightWidth;
        b.lightDirX = p.lightDirX;
''',
    '''        b.highlightWidth = p.highlightWidth;
        b.os4EdgeWidthPx = p.os4EdgeWidthPx;
        b.os4ReflectOffsetPx = p.os4ReflectOffsetPx;
        b.os4ReflectionStrength = p.os4ReflectionStrength;
        b.os4ReflectionLighten = p.os4ReflectionLighten;
        b.os4DirectionalAngleRange = p.os4DirectionalAngleRange;
        b.os4DirectionalIntensity = p.os4DirectionalIntensity;
        b.os4DirectionalOppositeIntensity = p.os4DirectionalOppositeIntensity;
        b.lightDirX = p.lightDirX;
''')

replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java",
    '''        public float os4EdgeWidthPx = 0f;
        public float os4ReflectOffsetPx = 0f;
''',
    '''        public float os4EdgeWidthPx = 20f;
        public float os4ReflectOffsetPx = 10f;
''')

# 5) Existing highlight component controls gate only OS4 directional lighting, never reflection.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java",
    '''        corrected = gateExactlyOnce(corrected,
                "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);",
                "u_componentPressGlow", "press glow");
        return corrected;
''',
    '''        corrected = gateExactlyOnce(corrected,
                "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);",
                "u_componentPressGlow", "press glow");
        corrected = replaceExactlyOnce(corrected,
                "* max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff;",
                "* max(u_os4DirectionalIntensity, 0.0) * u_componentLitRim * os4MainFalloff;",
                "OS4 main directional component");
        corrected = replaceExactlyOnce(corrected,
                "* max(u_os4DirectionalOppositeIntensity, 0.0) * os4OppositeFalloff;",
                "* max(u_os4DirectionalOppositeIntensity, 0.0) * u_componentOppositeRim * os4OppositeFalloff;",
                "OS4 opposite directional component");
        return corrected;
''')

# 6) GUI: all seven values live alongside the existing Prismal numeric controls.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    "liquid_prismal_plain_highlight" -> "基础边缘高光"
    "liquid_prismal_light_dir_x" -> "光源水平方向"
''',
    '''    "liquid_prismal_plain_highlight" -> "基础边缘高光"
    "liquid_os4_edge_width_px" -> "OS4 背景驱动边缘带宽度；越大，参与反射与柔光的玻璃边缘越厚"
    "liquid_os4_reflect_offset_px" -> "OS4 沿边缘伪法线采样背景的反射偏移距离"
    "liquid_os4_reflection_strength" -> "OS4 背景反射与当前玻璃颜色的混合强度"
    "liquid_os4_reflection_lighten" -> "OS4 方向光对暗色背景的灰白补光强度"
    "liquid_os4_directional_angle_range" -> "OS4 方向光角度软衰减范围，相对 π 的百分比"
    "liquid_os4_directional_intensity" -> "OS4 主方向光强度；受现有“受光侧边缘”开关控制"
    "liquid_os4_directional_opposite_intensity" -> "OS4 反向补光强度；受现有“背光侧边缘”开关控制"
    "liquid_prismal_light_dir_x" -> "光源水平方向"
''')

replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "基础高光", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "光源 X", "%"),
''',
    '''    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "基础高光", "%"),
    IntSpec(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX, "OS4 边缘带宽度", "px"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX, "OS4 反射偏移", "px"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH, "OS4 反射强度", "%"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN, "OS4 暗部补光", "%"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE, "OS4 方向光角度范围", "%π"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY, "OS4 主方向光强度", "%"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY, "OS4 反向补光强度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "光源 X", "%"),
''')

print("OS4 edge GUI/gate patch applied successfully")
