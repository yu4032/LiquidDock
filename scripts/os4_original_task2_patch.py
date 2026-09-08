from pathlib import Path

P = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java')
s = P.read_text()


def once(old: str, new: str, label: str):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected one anchor, found {n}')
    s = s.replace(old, new, 1)

# Declare only the controls supported by the extracted background-driven model.
anchor = '    private static final String OS4_EDGE_HELPERS = """\n'
uniforms = '''    private static final String OS4_EDGE_UNIFORMS = """
            uniform float u_os4EdgeWidthPx;
            uniform float u_os4ReflectOffsetPx;
            uniform float u_os4ReflectionStrength;
            uniform float u_os4ReflectionLighten;
            uniform float u_os4DirectionalAngleRange;
            uniform float u_os4DirectionalIntensity;
            uniform float u_os4DirectionalOppositeIntensity;
            """;

'''
once(anchor, uniforms + anchor, 'uniform declaration block')

once(
    '+ PRECISION + "\\n\\n" + OS4_EDGE_HELPERS)',
    '+ PRECISION + "\\n\\n" + OS4_EDGE_UNIFORMS + "\\n" + OS4_EDGE_HELPERS)',
    'precision uniform injection')

old_edge = '''                        + "    // OS4 keeps material thickness separate from the visible Bloom width.\\n"
                        + "    float os4EdgePx = clamp(minDim * 0.060, 6.0, 18.0);\\n"
                        + "    float os4ThicknessPx = max(u_glassThickness, os4EdgePx + 6.0);\\n"
                        + "    float os4ReflectOffsetPx = clamp(os4ThicknessPx * 0.38, 4.0, 14.0);\\n"
                        + "    float os4EdgeDepth = clamp(edgeDist / max(os4EdgePx, 1.0), 0.0, 1.0);")'''
new_edge = '''                        + "    // OS4 edge width controls the internal optical band, never output alpha.\\n"
                        + "    float os4EdgePx = u_os4EdgeWidthPx > 0.0\\n"
                        + "            ? u_os4EdgeWidthPx : clamp(minDim * 0.060, 6.0, 18.0);\\n"
                        + "    float os4ThicknessPx = max(u_glassThickness, os4EdgePx + 6.0);\\n"
                        + "    float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0\\n"
                        + "            ? u_os4ReflectOffsetPx : clamp(os4ThicknessPx * 0.38, 4.0, 14.0);\\n"
                        + "    float os4EdgeT = clamp(edgeDist / max(os4EdgePx, 1.0), 0.0, 1.0);\\n"
                        + "    float os4EdgeRemain = 1.0 - os4EdgeT;\\n"
                        + "    float os4EdgeDepth = os4EdgeT;")'''
once(old_edge, new_edge, 'edge width/depth block')

# Replace the old normalized 2D-only SDF normal with OS4-style centered gradient + pseudo-3D normal.
start = s.index('                .replace(OUTWARD, OUTWARD + "\\n"')
end = s.index('                .replace(MENISCUS_BLEND,', start)
new_outward = '''                .replace(OUTWARD, OUTWARD + "\\n"
                        + "    float sdfNormalBlend = os4EdgeBand(edgeDist, "
                        + "max(edgePixelFootprint * 2.0, "
                        + "clamp(minDim * 0.055 * opticalEdgeScale, 2.0, 12.0)), edgeAa);\\n"
                        + "    float sdfXp = sdRoundBox(pPx + vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\\n"
                        + "    float sdfXn = sdRoundBox(pPx - vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\\n"
                        + "    float sdfYp = sdRoundBox(pPx + vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\\n"
                        + "    float sdfYn = sdRoundBox(pPx - vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\\n"
                        + "    vec2 sdfEdgeGradient = 0.5 * vec2(\\n"
                        + "            (sdfXp - sdfXn) / max(edgePixelStep.x, 1e-3),\\n"
                        + "            (sdfYp - sdfYn) / max(edgePixelStep.y, 1e-3));\\n"
                        + "    sdfEdgeGradient.y = -sdfEdgeGradient.y;\\n"
                        + "    vec3 os4EdgeNormal3 = normalize(vec3(sdfEdgeGradient, 1.0));\\n"
                        + "    vec2 sdfEdgeNormal = normalize(os4EdgeNormal3.xy + vec2(1e-5));\\n"
                        + "    if (sdfNormalBlend > 0.001) {\\n"
                        + "        outward = normalize(mix(outward, sdfEdgeNormal, sdfNormalBlend));\\n"
                        + "    }\\n"
                        + "    vec2 opticalEdgeNormal = outward;\\n"
                        + "    os4EdgeNormal3 = normalize(vec3(opticalEdgeNormal, "
                        + "max(os4EdgeNormal3.z, 0.15)));")
'''
s = s[:start] + new_outward + s[end:]

# Replace the previous fixed-weight reflection with the extracted background-driven reflection semantics.
start = s.index('                .replace(REFLECTION_MIX, REFLECTION_MIX + "\\n"')
end = s.index('                .replace(RIM_BAND_RADIUS,', start)
new_reflect = '''                .replace(REFLECTION_MIX, REFLECTION_MIX + "\\n"
                        + "    vec2 os4ReflectUvOffset = opticalEdgeNormal\\n"
                        + "            * (2.0 * os4EdgeNormal3.z)\\n"
                        + "            * os4ReflectOffsetPx * os4EdgeRemain / u_resolution\\n"
                        + "            * os4VolumeMask;\\n"
                        + "    vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset, "
                        + "vec2(0.0), vec2(1.0));\\n"
                        + "    vec3 os4EdgeReflection = texture2D(u_blurredTexture, os4ReflectUv).rgb;\\n"
                        + "    if (u_useBlurredTexture != 1) {\\n"
                        + "        os4EdgeReflection = texture2D(u_backgroundTexture, os4ReflectUv).rgb;\\n"
                        + "    }\\n"
                        + "    float os4ReflectMask = (1.0 - os4EdgeCurve(os4EdgeT)) * os4VolumeMask;\\n"
                        + "    float os4EdgeReflectionWeight = clamp(os4ReflectMask "
                        + "* max(u_os4ReflectionStrength, 0.0), 0.0, 1.0);\\n"
                        + "    color = mix(color, os4EdgeReflection, os4EdgeReflectionWeight);")
'''
s = s[:start] + new_reflect + s[end:]

# Remove the independent white/cool Bloom halo and replace it with directional gain + dark-background lift.
start = s.index('                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\\n"')
end_marker = '\n    }\n\n    private static String resolveBaseOffsetAnchor'
end = s.index(end_marker, start)
new_tail = '''                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\\n"
                        + "\\n"
                        + "    // Extracted OS4 model: directional light modulates the current backdrop-driven glass.\\n"
                        + "    vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));\\n"
                        + "    float os4A = clamp(dot(os4EdgeNormal3, os4LightDir3), -1.0, 1.0);\\n"
                        + "    float os4B = clamp(2.0 * os4EdgeNormal3.z * os4LightDir3.z - os4A, -1.0, 1.0);\\n"
                        + "    float os4AngleRange = max(u_os4DirectionalAngleRange, 0.05);\\n"
                        + "    float os4MainAngle = acos(clamp(os4A, 0.0, 1.0));\\n"
                        + "    float os4OppositeAngle = acos(clamp(os4B, 0.0, 1.0));\\n"
                        + "    float os4MainFalloff = max(1.0 - os4MainAngle / (3.14159265 * os4AngleRange), 0.0);\\n"
                        + "    float os4OppositeFalloff = max(1.0 - os4OppositeAngle / (3.14159265 * os4AngleRange), 0.0);\\n"
                        + "    float os4DirectionalMain = max(os4A, 0.0) "
                        + "* max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff;\\n"
                        + "    float os4DirectionalOpposite = max(os4B, 0.0) "
                        + "* max(u_os4DirectionalOppositeIntensity, 0.0) * os4OppositeFalloff;\\n"
                        + "    float os4EdgeMask = (1.0 - os4EdgeCurve(os4EdgeT)) * os4VolumeMask;\\n"
                        + "    float os4SoftLight = clamp((os4DirectionalMain + os4DirectionalOpposite) "
                        + "* os4EdgeMask, 0.0, 1.0);\\n"
                        + "    float os4DirectionalGain = 1.0 + smoothstep(0.0, 1.0, os4SoftLight);\\n"
                        + "    color *= os4DirectionalGain;\\n"
                        + "    float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\\n"
                        + "    float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);\\n"
                        + "    color += vec3(u_os4ReflectionLighten * os4DarkResponse\\n"
                        + "            * max(os4DirectionalGain - 1.0, 0.0));");'''
s = s[:start] + new_tail + s[end:]

if 'os4BloomRing' in s or 'os4BloomColor' in s:
    raise SystemExit('independent Bloom halo survived patch')

P.write_text(s)
