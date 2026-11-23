#version 450

layout(location = 0) in vec2 v_texcoord;
layout(location = 1) in vec3 v_screen_pos;
layout(location = 0) out vec4 fragColor;

/* 384-byte (0x180) UBO layout compatible with MiGlassV13F16FragmentShader.
 * Impeller populates uResolution (0x00), uGeoPxSize (0x10), uAlpha (0x7c),
 * uInvResolution (0x80), uScale (0x88), uUseMaskTex (0x90), colorSpaceConvertMode (0x160).
 * BindUBO hook injects v2.7.9 Prismal uniform values into the remaining slots. */
layout(set = 0, binding = 64, std140) uniform UBO {
    vec2  uResolution;               // 0x00
    vec2  p_backdropSampleScale;     // 0x08
    vec3  uGeoPxSize;                // 0x10
    float p_refractionInset;         // 0x1c
    float p_sminSmoothing;           // 0x20
    float p_edgeRefractionFalloff;   // 0x24
    float p_ior;                     // 0x28
    vec3  p_invariantLightPrimary;   // 0x30
    float p_glassThickness;          // 0x3c
    float p_normalStrength;          // 0x40
    float p_displacementScale;       // 0x44
    float p_heightTransitionWidth;   // 0x48
    float p_lensRefractionPx;        // 0x4c
    float p_lensDepthEffect;         // 0x50
    float p_chromaticAberration;     // 0x54
    float p_dispersionR;             // 0x58
    float p_dispersionB;             // 0x5c
    vec4  p_glassColor;              // 0x60
    float p_vibrancy;                // 0x70
    float p_brightness;              // 0x74
    float p_liquidDome;              // 0x78
    float uAlpha;                    // 0x7c
    vec2  uInvResolution;            // 0x80
    vec2  uScale;                    // 0x88
    uint  uUseMaskTex;               // 0x90
    uint  p_flags;                   // 0x94 (bit0=contCorners, bit1=useBlur, bit2=showNormals, bit3=hasSharpTex)
    float p_fresnelReflect;          // 0x98
    float p_invariantFresnelR0;      // 0x9c
    vec2  p_invariantLightXY;        // 0xa0
    vec2  p_parallaxAndPad;          // 0xa8 (.x = parallaxScale)
    vec3  p_invariantOs4LightDir;    // 0xb0
    float p_caustics;                // 0xbc
    vec4  p_os4EdgeParams;           // 0xc0 (edgeWidthPx, reflectOffsetPx, reflectionStrength, reflectionLighten)
    vec4  p_os4LightParams;          // 0xd0 (angleRange, dirIntensity, dirOppIntensity, highlightWidth)
    vec4  p_baseColor;               // 0xe0
    vec4  p_shadowColor;             // 0xf0
    vec4  p_miscParams;              // 0x100 (shadowExtent, transmittance, lensRefractionScale, highlightMask)
    vec4  p_backlitParams;           // 0x110 (backlitWidthPx, backlitStrength, backlitSideStart, backlitSideSpan)
    vec4  p_castShadowColor;         // 0x120 (r, g, b, a)
    vec4  p_castShadowParams;        // 0x130 (radiusPx, offsetYPx, lightFollow, _)
    vec4  p_reserved[2];             // 0x140..0x15f
    int   colorSpaceConvertMode;     // 0x160
} ubo;

layout(set = 0, binding = 65) uniform sampler2D u_blurredTexture;
layout(set = 0, binding = 66) uniform sampler2D u_backgroundTexture;
layout(set = 0, binding = 67) uniform sampler2D uSdfTex;

float sdLegacyRoundedRect(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

vec2 gradLegacyRoundedRect(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        vec2 m = max(cornerCoord, 0.0);
        float len = length(m);
        if (len < 1e-5) return vec2(0.0);
        return sign(coord) * (m / len);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * vec2(gradX, 1.0 - gradX);
    }
}

vec2 continuousGlassReach(float r, vec2 b) {
    vec2 room = clamp((b - vec2(r)) / max(r, 1e-6), 0.0, 1.0);
    return r * min(0.2893 * pow(room, vec2(0.6)), room);
}

float sdContinuousGlass(vec2 p, vec2 b, float radius) {
    vec2 e = abs(p) - b;
    float box = min(max(e.x, e.y), 0.0) + length(max(e, vec2(0.0)));
    float r = clamp(radius, 0.0, min(b.x, b.y));
    if (r <= 0.001) return box;
    vec2 reach = continuousGlassReach(r, b);
    vec2 R = min(vec2(r) + reach, b);
    vec2 q = abs(p) - (b - R);
    if (min(q.x, q.y) <= 0.0) return box;
    vec2 n = vec2(2.0) + 0.7198 * reach / max(0.2893 * r, 1e-6);
    vec2 shares = pow(q / R, n);
    float total = shares.x + shares.y;
    if (total <= 1e-20) return box;
    vec2 weight = shares / total;
    float corner = dot(R, weight) * (pow(total, 1.0 / dot(n, weight)) - 1.0);
    return max(corner, box);
}

float sdRoundedRectRealistic(vec2 p, vec2 b, float r, bool cont) {
    return cont ? sdContinuousGlass(p, b, r) : sdLegacyRoundedRect(p, b, r);
}

float circleMapRealistic(float x) {
    x = clamp(x, 0.0, 1.0);
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

float smin_poly(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax_poly(float a, float b, float k) {
    if (k <= 0.0) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
    return mix(b, a, h) + k * h * (1.0 - h);
}

float sdLegacyRoundBox(vec2 p, vec2 b, float r, float k) {
    if (k <= 0.0) {
        vec2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }
    vec2 q = abs(p) - b + r;
    float a = smax_poly(q.x, q.y, k);
    float c = smin_poly(a, 0.0, k * 0.5);
    vec2 ql = vec2(smax_poly(q.x, 0.0, k), smax_poly(q.y, 0.0, k));
    return c + length(ql) - r;
}

float sdRoundBox(vec2 p, vec2 b, float r, float k, bool cont) {
    return cont ? sdContinuousGlass(p, b, r) : sdLegacyRoundBox(p, b, r, k);
}

float getHeightFromDist(float dist, float tw) {
    float t = clamp(-dist / tw, 0.0, 1.0);
    return sqrt(max(0.0, 2.0 * t - t * t));
}

vec2 computeGradientHeight(vec2 pPx, vec2 halfSz, float cr, float k, float tw, bool cont) {
    vec2 s = vec2(cont ? max(k, 1.0) : 1.0);
    float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(s.x, 0.0), halfSz, cr, k, cont), tw);
    float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(s.x, 0.0), halfSz, cr, k, cont), tw);
    float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0.0, s.y), halfSz, cr, k, cont), tw);
    float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0.0, s.y), halfSz, cr, k, cont), tw);
    return vec2(hpx - hnx, hpy - hny) * 0.5 / s;
}

vec3 applyVibrancy(vec3 rgb, float sat) {
    if (abs(sat - 1.0) <= 0.001) return rgb;
    float L = dot(rgb, vec3(0.213, 0.715, 0.072));
    return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);
}

float os4EdgeCurve(float t) {
    t = clamp(t, 0.0, 1.0);
    if (t >= 0.85) return 1.0;
    float shoulder = clamp(mix(0.5, 1.0, sqrt(t)), 0.0, 1.0);
    float smoother = shoulder * shoulder * shoulder
            * (shoulder * (shoulder * 6.0 - 15.0) + 10.0);
    float shaped = clamp((smoother - 0.5) * 2.0, 0.0, 1.0);
    return 1.0 - pow(1.0 - shaped, 1.35);
}

float os4EdgeBand(float edgeDist, float width, float aa) {
    float safeAa = max(aa, 1e-3);
    float safeWidth = max(width, safeAa * 1.5);
    float insideDist = max(edgeDist, 0.0);
    float proximity = clamp(1.0 - insideDist / safeWidth, 0.0, 1.0);
    float shaped = os4EdgeCurve(proximity) * proximity;
    float innerAa = 1.0 - smoothstep(max(safeWidth - safeAa, 0.0), safeWidth + safeAa, insideDist);
    float outerAa = smoothstep(-safeAa, 0.0, edgeDist);
    return clamp(shaped * innerAa * outerAa, 0.0, 1.0);
}

const vec3 OPTICAL_SPECULAR_RGB = vec3(0.98, 0.992, 1.0);

float opticalSmoother01(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float opticalLensArc(float t) {
    t = clamp(t, 0.0, 1.0);
    return sqrt(max((2.0 - t) * t, 0.0));
}

float opticalOpposingFill(float facing, float keyIntensity, float fillIntensity) {
    float key = opticalSmoother01((facing - 0.06) / 0.94);
    float fill = opticalSmoother01((-facing - 0.06) / 0.94);
    float keyKnee = key / (1.0 + 0.35 * (1.0 - key));
    float fillKnee = fill / (1.0 + 0.35 * (1.0 - fill));
    return clamp(
            keyKnee * (0.72 + 0.24 * clamp(keyIntensity, 0.0, 1.0))
            + fillKnee * (0.66 + 0.20 * clamp(fillIntensity, 0.0, 1.0)),
            0.0, 1.0);
}

float opticalErfcHalf(float distance) {
    float u = clamp(0.25 * distance + 0.5, 0.0, 1.0);
    float x = 4.0 * u - 2.0;
    float x2 = x * x;
    float p = 0.002954;
    p = p * x2 - 0.034460;
    p = p * x2 + 0.168210;
    p = p * x2 - 0.560550;
    return clamp(p * x + 0.5, 0.0, 1.0);
}

vec3 shadeOpticalMeniscus(vec3 transmitted, float darkWeight, float specEnergy) {
    float backdropLuma = dot(transmitted, vec3(0.2126, 0.7152, 0.0722));
    float darkFloor = mix(0.46, 0.28, smoothstep(0.18, 0.72, backdropLuma));
    float baseReflectance = 0.09 * (1.0 - smoothstep(0.12, 0.58, backdropLuma));
    vec3 baseContour = mix(transmitted * 0.72, OPTICAL_SPECULAR_RGB, baseReflectance);
    vec3 darkContour = transmitted * darkFloor;
    float incidence = clamp(darkWeight, 0.0, 1.0);
    float sideDarkening = opticalSmoother01((incidence - 0.25) / 0.75);
    float reflectiveLift = opticalSmoother01((0.62 - incidence) / 0.62)
            * (0.27 + 0.18 * clamp(specEnergy, 0.0, 1.0));
    vec3 contour = mix(baseContour, darkContour, sideDarkening);
    return clamp(mix(contour, OPTICAL_SPECULAR_RGB, reflectiveLift), vec3(0.0), vec3(1.0));
}

vec3 optical27Grade(vec3 rgb, vec3 gain, vec3 bias) {
    return clamp(rgb * gain + bias, vec3(0.0), vec3(1.0));
}

vec3 optical27BlurFill(vec3 face, vec3 fill, float lighten, float darken, float normal) {
    float l = clamp(lighten, 0.0, 1.0);
    float d = clamp(darken, 0.0, 1.0 - l);
    float base = 1.0 - l - d;
    vec3 mixed = l * max(face, fill) + d * min(face, fill) + base * face;
    return mix(mixed, fill, clamp(normal, 0.0, 1.0));
}

float optical27BlurRamp(float depth, float reach) {
    float r = max(reach, 0.01);
    float a = smoothstep(0.0, r * 0.30, depth);
    float b = smoothstep(r * 0.30, r * 0.65, depth);
    float c = smoothstep(r * 0.65, r, depth);
    return clamp(1.0 - 0.43 * a - 0.33 * b - 0.24 * c, 0.0, 1.0);
}

vec2 backdropUv(vec2 screenUv, vec2 offset) {
    vec2 s = max(ubo.p_backdropSampleScale, vec2(0.01));
    vec2 scaled = (screenUv - 0.5) / s + 0.5;
    return clamp(scaled + offset, vec2(0.001), vec2(0.999));
}

vec3 sampleSharpBackdrop(vec2 uv, bool hasSharpTex) {
    return hasSharpTex ? texture(u_backgroundTexture, uv).rgb : texture(u_blurredTexture, uv).rgb;
}

vec3 sampleBodyBackdrop(vec2 uv, bool useBlur, bool hasSharpTex) {
    return (useBlur || !hasSharpTex) ? texture(u_blurredTexture, uv).rgb : texture(u_backgroundTexture, uv).rgb;
}

vec3 optical27SevenTap(vec2 screenUv, vec2 offset, vec2 push, bool useBlur, bool hasSharpTex) {
    vec3 accum = vec3(0.0);
    for (int i = 0; i < 3; ++i) {
        float w = 1.0 - float(i) / 3.0;
        vec2 uv = backdropUv(screenUv, offset + push * w * ubo.p_dispersionR);
        vec3 c = sampleBodyBackdrop(uv, useBlur, hasSharpTex);
        accum.r += c.r * w;
        accum.g += c.g * (1.0 - w);
    }
    for (int j = 0; j < 4; ++j) {
        float w = float(j) / 3.0;
        vec2 uv = backdropUv(screenUv, offset - push * w * ubo.p_dispersionB);
        vec3 c = sampleBodyBackdrop(uv, useBlur, hasSharpTex);
        accum.g += c.g * (1.0 - w);
        accum.b += c.b * w;
    }
    return accum * vec3(0.5, 1.0 / 3.0, 0.5);
}

void main() {
    bool contCorners = (ubo.p_flags & 1u) != 0u;
    bool useBlur     = (ubo.p_flags & 2u) != 0u;
    bool showNormals = (ubo.p_flags & 4u) != 0u;
    bool hasSharpTex = (ubo.p_flags & 8u) != 0u;

    vec2 glassSize = max(ubo.uGeoPxSize.xy, vec2(2.0));
    float padPx = max(ubo.p_castShadowParams.w, 0.0);
    vec2 innerSize = max(glassSize - vec2(padPx * 2.0), vec2(2.0));
    vec2 halfSz = innerSize * 0.5;
    float minDim = min(halfSz.x, halfSz.y);
    float pxNorm = clamp(minDim / 108.0, 0.36, 1.0) + smoothstep(88.0, 220.0, minDim) * 0.45;
    float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);
    float cr = min(max(ubo.uGeoPxSize.z, 1.0), minDim);

    // Match prismal_fragment.glsl coordinates:
    // cKy: visual TOP = -y, visual BOTTOM = +y (matches Vulkan v_texcoord)
    // pPx: visual TOP = +y, visual BOTTOM = -y (matches prismal_fragment.glsl)
    vec2 cKy = (v_texcoord - 0.5) * glassSize;
    vec2 pPx = vec2(cKy.x, -cKy.y);

    // Screen UV for backdrop textures (computed before discard for uniform-control-flow derivatives)
    float sz = abs(v_screen_pos.z) < 1e-6 ? (v_screen_pos.z < 0.0 ? -1e-6 : 1e-6) : v_screen_pos.z;
    vec2 screenPx = v_screen_pos.xy / sz;
    vec2 screenUv = (screenPx - ubo.uResolution) * ubo.uInvResolution;

    // Orientation/scale/sub-pass invariant mapping between logical cKy pixels and framebuffer screenUv.
    // Handles Vulkan 0/90/180/270 pre-rotation and cropped sub-pass textures automatically.
    mat2 J_ky = mat2(dFdx(cKy), dFdy(cKy));
    mat2 J_uv = mat2(dFdx(screenUv), dFdy(screenUv));
    float detKy = J_ky[0][0] * J_ky[1][1] - J_ky[1][0] * J_ky[0][1];
    mat2 invJ_ky;
    mat2 kyToUv;
    if (abs(detKy) > 1e-6) {
        invJ_ky = inverse(J_ky);
        kyToUv = J_uv * invJ_ky;
    } else {
        invJ_ky = mat2(1.0);
        kyToUv = mat2(ubo.uInvResolution.x, 0.0, 0.0, ubo.uInvResolution.y);
    }

    // Pure analytical v2.7.9 Prismal SDF (unclamped Euclidean distance across full glass interior)
    bool outOfUnit = (v_texcoord.x < 0.0 || v_texcoord.x > 1.0 || v_texcoord.y < 0.0 || v_texcoord.y > 1.0);
    float distMask = sdRoundBox(pPx, halfSz, cr, ubo.p_sminSmoothing, contCorners);
    float sdKy = sdRoundedRectRealistic(cKy, halfSz, cr, contCorners);
    float edgeDist = -distMask;
    float opticalEdgeScale = clamp(ubo.p_os4LightParams.w, 0.5, 3.0);
    vec2 os4SdfGradient = vec2(
            (sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, cr, ubo.p_sminSmoothing, contCorners)
           - sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, cr, ubo.p_sminSmoothing, contCorners)) * 0.5,
           -(sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, cr, ubo.p_sminSmoothing, contCorners)
           - sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, cr, ubo.p_sminSmoothing, contCorners)) * 0.5);
    float edgeAa = max(max(fwidth(distMask), abs(os4SdfGradient.x) + abs(os4SdfGradient.y)), 0.75);
    float os4DefaultEdgeWidthPx = clamp(6.0 * opticalEdgeScale, 3.0, 18.0);
    float os4EdgeWidthPx = ubo.p_os4EdgeParams.x > 0.0 ? ubo.p_os4EdgeParams.x : os4DefaultEdgeWidthPx;
    float os4EdgeT = clamp(edgeDist / max(os4EdgeWidthPx, 1.0), 0.0, 1.0);
    float os4EdgeMask = os4EdgeBand(edgeDist, os4EdgeWidthPx, edgeAa);

    float reflShell = smoothstep(clamp(minDim * 0.09 * opticalEdgeScale, 1.8, 18.0), 0.0, edgeDist)
            * smoothstep(-3.0, 0.0, distMask);
    reflShell *= mix(0.78, 0.42, smallGlass);

    float insetShiftPx = clamp((ubo.p_refractionInset - 20.0) * 0.25,
            -minDim * 0.05, minDim * 0.12 * opticalEdgeScale);
    float surfaceDistance = distMask + insetShiftPx;
    float aaWidth = max(edgeAa * 1.5, 2.0);
    float aaOuter = min(-insetShiftPx, 0.0);
    float opacity = outOfUnit ? 0.0 : (1.0 - smoothstep(aaOuter - aaWidth, aaOuter, distMask));
    if (ubo.uUseMaskTex != 0u && !hasSharpTex) {
        opacity *= texture(u_backgroundTexture, v_texcoord).a;
    }

    // PrismalEdgeDecorationShader: independent exterior cast shadow (underlay)
    float castShadowRadiusPx = ubo.p_castShadowParams.x;
    float castShadowOffsetYPx = ubo.p_castShadowParams.y;
    float castShadowLightFollow = ubo.p_castShadowParams.z;
    float exteriorShadowAlpha = 0.0;
    if (!outOfUnit && surfaceDistance > 0.0 && castShadowRadiusPx > 0.0 && ubo.p_castShadowColor.a > 0.0) {
        vec2 shadowNormal = normalize(os4SdfGradient + vec2(0.00001));
        float facing = dot(shadowNormal, ubo.p_invariantLightXY);
        float projected = -facing * castShadowRadiusPx * 0.42 * castShadowLightFollow
                - shadowNormal.y * castShadowOffsetYPx;
        float distanceToCaster = max(0.0, surfaceDistance - projected);
        float halo = 1.0 - smoothstep(0.0, max(castShadowRadiusPx * 2.0, 0.001), distanceToCaster);
        exteriorShadowAlpha = ubo.p_castShadowColor.a * halo * halo
                * clamp(ubo.uAlpha * ubo.p_miscParams.y, 0.0, 1.0);
    }

    // PrismalEdgeDecorationShader: two-band optical meniscus contour & internal glint
    float backlitWidthPx = ubo.p_backlitParams.x;
    float backlitStrength = ubo.p_backlitParams.y;
    float backlitSideStart = ubo.p_backlitParams.z;
    float backlitSideSpan = ubo.p_backlitParams.w;
    float outlineAlpha = 0.0;
    float innerHighlightAlpha = 0.0;
    float opticalDark = 0.0;
    float opticalSpec = 0.0;
    vec3 edgeTransmission = vec3(0.0);
    if (backlitStrength > 0.0 && backlitWidthPx > 0.0 && backlitSideStart < 1.0 && !outOfUnit) {
        float aa = max(edgeAa, 0.75);
        float outlineWidthPx = max(backlitWidthPx, 0.5);
        // When quad is expanded by padPx, draw outer contour right outside the glass rim (v2.7.9 exact);
        // otherwise shift inward by outlineWidthPx so it fits inside the [0,1] quad.
        float rimDistance = (padPx >= outlineWidthPx) ? surfaceDistance : (surfaceDistance + outlineWidthPx);
        float outlineEntry = smoothstep(-aa * 0.70, aa * 0.20, rimDistance);
        float outlineExit = 1.0 - smoothstep(
                max(outlineWidthPx - aa * 0.5, aa * 0.5),
                outlineWidthPx + aa * 0.5, rimDistance);
        outlineAlpha = clamp(backlitStrength * outlineEntry * outlineExit, 0.0, 0.98);
        if (padPx < outlineWidthPx) outlineAlpha *= opacity;

        float highlightWidthPx = clamp(outlineWidthPx * 0.70 + aa * 0.65, 1.0, 3.5);
        float innerBand = smoothstep(
                -highlightWidthPx - aa * 0.6,
                -highlightWidthPx + aa * 0.30, rimDistance)
                * (1.0 - smoothstep(-aa * 0.10, aa * 0.75, rimDistance));

        if (outlineAlpha > 0.001 || innerBand > 0.001) {
            vec2 normal = normalize(os4SdfGradient + vec2(0.00001));
            float ax = abs(normal.x);
            float ay = abs(normal.y);
            float sideAxis = ax / max(ax + ay, 0.0001);
            float sideT = (sideAxis - backlitSideStart) / max(backlitSideSpan, 0.001);
            float sideWeight = opticalSmoother01(sideT);
            float horizontalWeight = 1.0 - sideWeight;
            float signedFacing = dot(normal, ubo.p_invariantLightXY);
            float opposingFill = opticalOpposingFill(
                    signedFacing, ubo.p_os4LightParams.y, ubo.p_os4LightParams.z);
            opticalDark = sideWeight * mix(1.0, 0.88, opposingFill);
            opticalSpec = horizontalWeight * mix(0.72, 1.0, opposingFill);
            innerHighlightAlpha = clamp(
                    backlitStrength * innerBand * horizontalWeight * mix(0.26, 0.46, opposingFill),
                    0.0, 0.52) * opacity;

            if (outlineAlpha > 0.001 || innerHighlightAlpha > 0.001) {
                float rimReflectPx = ubo.p_os4EdgeParams.y > 0.0
                        ? ubo.p_os4EdgeParams.y
                        : clamp(ubo.p_glassThickness * 0.38, 4.0, 14.0);
                float sizeCapPx = clamp(min(glassSize.x, glassSize.y) * 0.025, 3.0, 9.0);
                float meniscusWidthPx = clamp(os4EdgeWidthPx * 0.55, 2.5, 5.0);
                float opticalMeniscusProfile = 1.0 - smoothstep(0.0, meniscusWidthPx, abs(rimDistance));
                float innerT = clamp((max(-rimDistance, 0.0) + aa * 0.15) / meniscusWidthPx, 0.0, 1.0);
                float outerT = clamp((max(rimDistance, 0.0) + aa * 0.15) / meniscusWidthPx, 0.0, 1.0);
                float innerBendPx = max(ubo.p_lensRefractionPx, 0.0) * 0.14 * (1.0 - opticalLensArc(innerT));
                float outerBendPx = rimReflectPx * 0.35 * (1.0 - opticalLensArc(outerT));
                float refractionHandoff = smoothstep(-aa * 0.65, aa * 0.65, rimDistance);
                float bendPx = min(sizeCapPx,
                        mix(innerBendPx, outerBendPx, refractionHandoff)
                        * mix(0.35, 1.0, opticalMeniscusProfile));
                vec2 rimUv = backdropUv(screenUv, kyToUv * (-normal * bendPx));
                edgeTransmission = sampleSharpBackdrop(rimUv, hasSharpTex);
            }
        }
    }

    if (opacity < 0.001 && outlineAlpha < 0.001 && exteriorShadowAlpha < 0.001) discard;
    if (opacity < 0.001) {
        float combinedAlpha = outlineAlpha + exteriorShadowAlpha * (1.0 - outlineAlpha);
        if (combinedAlpha < 0.001) discard;
        vec3 outlineRgb = shadeOpticalMeniscus(edgeTransmission, opticalDark, opticalSpec);
        vec3 premul = outlineRgb * outlineAlpha
                + optical27Grade(ubo.p_castShadowColor.rgb, vec3(0.96), vec3(0.0))
                * exteriorShadowAlpha * (1.0 - outlineAlpha);
        fragColor = vec4(clamp(premul, 0.0, 1.0), combinedAlpha);
        return;
    }

    // Prismal v2.7.9 droplet height & surface normal
    float dome = clamp(ubo.p_liquidDome, 0.0, 2.0);
    float htw = max(ubo.p_heightTransitionWidth, 1.0);
    float refractionHeight = min(max(htw * (1.0 + 0.55 * dome), 1.0), minDim * 0.98);
    float tw = min(max(htw * (1.0 + 0.38 * dome), 1.0), minDim * 0.98);
    float hSig = getHeightFromDist(distMask, tw);
    vec2 gradHSig = computeGradientHeight(pPx, halfSz, cr, ubo.p_sminSmoothing, tw, contCorners);

    float gradRadius = min(cr * 1.5, minDim);
    vec2 gradLens = contCorners ? os4SdfGradient : gradLegacyRoundedRect(cKy, halfSz, gradRadius);

    float innerReach = max(minDim - cr * 0.42, minDim * 0.22);
    innerReach += refractionHeight * (1.0 + 0.25 * dome);
    innerReach = min(innerReach, max(halfSz.x, halfSz.y) * 0.95);
    float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);
    float tShell = 1.0 - tDeep;

    float meniscusBand = smoothstep(0.0, 0.12, tShell);
    float hDome = (pow(tShell, 0.38) + 0.10 * pow(tShell, 2.8)) * meniscusBand;
    float coreBlend = smoothstep(0.0, 0.38, tDeep);
    float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig, 0.4 + 0.6 * (1.0 - dome));
    float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
    float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
    float height = clamp(mix(hSlab, hDome, domeW) * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);

    vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);
    vec3 os4EdgeNormal3 = normalize(vec3(os4SdfGradient, 1.0));
    float shellCurv = smoothstep(0.0, 1.0, tShell);
    vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0))) * meniscusBand * edgeRound;
    vec2 gradH = mix(gradHSig, gCap, domeW);

    vec3 N = normalize(vec3(-gradH.x * ubo.p_normalStrength, -gradH.y * ubo.p_normalStrength, 1.0));
    float menW = clamp(edgeDist / tw, 0.0, 1.0);
    float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
    vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
    float menBlend = smoothstep(tw * 0.42 * opticalEdgeScale, 0.0, edgeDist) * smoothstep(-4.0, 0.0, distMask) * 0.62;
    menBlend *= mix(1.0, 0.15, smallGlass);
    N = normalize(mix(N, N_meniscus, menBlend));

    // Fresnel
    vec3 V = vec3(0.0, 0.0, 1.0);
    float cosVN = clamp(dot(N, V), 0.0, 1.0);
    float r0 = ubo.p_invariantFresnelR0;
    float silW = clamp(minDim * 0.12 * opticalEdgeScale, 2.5, 34.0);
    float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);
    float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);
    float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);
    float cosVNrim = mix(cosVN, max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);
    float F = r0 + (1.0 - r0) * pow(1.0 - cosVNrim, 5.0);
    float fresCtl = clamp(ubo.p_fresnelReflect, 0.0, 5.0);

    // PrismalSingleEdgeShader: single monotonic SDF edge lens
    vec2 lensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);
    if (ubo.p_lensDepthEffect > 0.001) {
        float cornerWeight = smoothstep(0.55, 0.95, abs(cKy.x) / max(halfSz.x, 1.0))
                           * smoothstep(0.55, 0.95, abs(cKy.y) / max(halfSz.y, 1.0));
        if (cornerWeight > 0.0) {
            vec2 radialDir = normalize(cKy + vec2(1e-4, 1e-4));
            vec2 bentDir = lensDir + radialDir * clamp(ubo.p_lensDepthEffect, 0.0, 1.0) * cornerWeight;
            lensDir = length(bentDir) > 1e-5 ? normalize(bentDir) : vec2(0.0);
        }
    }

    float dLens = 0.0;
    if ((-sdKy) < refractionHeight) {
        dLens = circleMapRealistic(1.0 - (-min(sdKy, 0.0) / refractionHeight)) * (-ubo.p_lensRefractionPx);
    }
    vec2 baseOffset = kyToUv * (dLens * lensDir);
    vec2 uvCenter = backdropUv(screenUv, baseOffset);
    float avgDim = (glassSize.x + glassSize.y) * 0.5;

    // Chromatic dispersion: 7-tap spectral curve from PrismalReconstructed27Shader + pixel-domain scale
    float caAmt = max(ubo.p_chromaticAberration, 0.0);
    vec3 color;
    if (caAmt < 0.02) {
        color = sampleBodyBackdrop(uvCenter, useBlur, hasSharpTex);
    } else {
        float chromaReachPx = min(refractionHeight, clamp(minDim * 0.12, 4.0, 14.0));
        float chromaDepthPx = max(edgeDist, 0.0);
        float edgeFac = 1.0 - smoothstep(0.0, chromaReachPx, chromaDepthPx);
        float maxChannelDispersion = max(1.0, max(abs(ubo.p_dispersionR), abs(ubo.p_dispersionB)));
        float chromaCapPx = clamp(minDim * 0.018, 0.75, 2.5);
        float chromaRawPx = caAmt * 0.0018 * minDim * pxNorm;
        float chromaPx = min(chromaRawPx, chromaCapPx / maxChannelDispersion) * edgeFac;
        float normalLength = length(os4SdfGradient);
        if (chromaPx > 0.02 && normalLength > 0.05) {
            vec2 surfaceNormal = os4SdfGradient / normalLength;
            vec2 chromaPush = kyToUv * (surfaceNormal * chromaPx);
            color = optical27SevenTap(screenUv, baseOffset, chromaPush, useBlur, hasSharpTex);
        } else {
            color = sampleBodyBackdrop(uvCenter, useBlur, hasSharpTex);
        }
    }

    color = applyVibrancy(color, ubo.p_vibrancy);

    // Body Fresnel reflection
    vec2 gDir = normalize(gradLens + vec2(1e-4));
    float edgeG = reflShell * pow(1.0 - cosVNrim, 1.35) * mix(0.07, 0.62, F);
    edgeG *= mix(0.72, 0.38, smallGlass);
    float reflW = min(0.9, edgeG * (0.1 + fresCtl * 0.46) * (0.28 + 0.72 * height));
    vec2 reflDeltaPx = gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) * pxNorm;
    vec2 reflUv = clamp(uvCenter + kyToUv * reflDeltaPx, vec2(0.001), vec2(0.999));
    vec3 reflSample = sampleBodyBackdrop(reflUv, useBlur, hasSharpTex);
    color = mix(color, reflSample, reflW);

    // PrismalOpticalEdgeShader: narrow sharp-backdrop meniscus reflection along OS4 SDF normal
    float os4DefaultReflectOffsetPx = clamp(ubo.p_glassThickness * 0.38, 4.0, 14.0);
    float os4ReflectOffsetPx = ubo.p_os4EdgeParams.y > 0.0 ? ubo.p_os4EdgeParams.y : os4DefaultReflectOffsetPx;
    vec2 os4ReflectPx = os4EdgeNormal3.xy * (2.0 * os4EdgeNormal3.z) * os4ReflectOffsetPx * (1.0 - os4EdgeT) * os4EdgeMask;
    vec2 os4ReflectUv = clamp(uvCenter + kyToUv * os4ReflectPx, vec2(0.001), vec2(0.999));
    float os4ReflectionWeight = clamp(os4EdgeMask * max(ubo.p_os4EdgeParams.z, 0.0), 0.0, 1.0);
    if (os4ReflectionWeight > 0.001) {
        color = mix(color, sampleSharpBackdrop(os4ReflectUv, hasSharpTex), os4ReflectionWeight);
    }

    // Sky haze
    vec3 skyHaze = vec3(0.88, 0.93, 1.02);
    float skyW = min(0.62, edgeG * pow(1.0 - cosVNrim, 1.2) * (0.04 + fresCtl * 0.28) * (0.28 + 0.72 * height));
    skyW *= mix(0.68, 0.32, smallGlass);
    color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);

    // Brightness + glass tint + inner shadow
    color *= ubo.p_brightness;
    color = mix(color, color * ubo.p_glassColor.rgb, ubo.p_glassColor.a);

    float shadowFalloff = avgDim * ubo.p_miscParams.x;
    float innerShadow = 1.0 - smoothstep(0.0, max(shadowFalloff, 1.0), edgeDist);
    innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);
    innerShadow *= 1.0 - smoothstep(0.0, clamp(minDim * 0.05, 0.6, 3.5), edgeDist) * 0.55;
    color = mix(color, ubo.p_shadowColor.rgb * 0.25, innerShadow * ubo.p_shadowColor.a);

    // PrismalOpticalEdgeShader: OS4 directional rim lighting (replaces legacy specular/litRim/oppRim)
    vec3 os4LightDir3 = ubo.p_invariantOs4LightDir;
    float os4MainFacing = max(dot(os4EdgeNormal3, os4LightDir3), 0.0);
    float os4OppositeFacing = max(dot(vec3(-os4EdgeNormal3.xy, os4EdgeNormal3.z), os4LightDir3), 0.0);
    float os4AngleRange = max(ubo.p_os4LightParams.x, 0.05);
    float os4MainAngle = acos(clamp(os4MainFacing, 0.0, 1.0));
    float os4OppositeAngle = acos(clamp(os4OppositeFacing, 0.0, 1.0));
    float os4MainFalloff = max(1.0 - os4MainAngle / (3.14159265 * os4AngleRange), 0.0);
    float os4OppositeFalloff = max(1.0 - os4OppositeAngle / (3.14159265 * os4AngleRange), 0.0);
    float os4Directional = os4MainFacing * max(ubo.p_os4LightParams.y, 0.0) * os4MainFalloff
            + os4OppositeFacing * max(ubo.p_os4LightParams.z, 0.0) * os4OppositeFalloff;
    float os4SoftLight = clamp(os4Directional * os4EdgeMask, 0.0, 1.0);
    float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);
    color += vec3(os4SoftLight * (0.42 + max(ubo.p_os4EdgeParams.w, 0.0) * os4DarkResponse));

    // Caustics
    if (ubo.p_caustics > 0.001) {
        float causticDot = dot(normalize(vec3(gradH * ubo.p_normalStrength, 0.45)), ubo.p_invariantLightPrimary);
        float caust = pow(max(causticDot, 0.0), 7.0) * ubo.p_caustics * height;
        color += caust * vec3(1.0, 0.96, 0.90);
    }

    // PrismalReconstructed27Shader: analytic ring shadow, holding tone, edge-bleed, and two-band composition
    vec3 ringShadedGlass = color;
    if (backlitStrength > 0.0 && surfaceDistance < 0.0) {
        vec3 faceGain = mix(vec3(1.0),
                clamp(ubo.p_glassColor.rgb, vec3(0.55), vec3(1.40)),
                0.045 * backlitStrength);
        ringShadedGlass = optical27Grade(ringShadedGlass,
                faceGain, vec3(0.008 * backlitStrength));

        if (useBlur) {
            float fillReach = clamp(ubo.p_heightTransitionWidth * 2.0, 8.0, 20.0);
            float blurBlend = optical27BlurRamp(max(-surfaceDistance, 0.0), fillReach);
            if (blurBlend > 0.001) {
                vec3 fillSample = texture(u_blurredTexture, uvCenter).rgb;
                vec3 gradedFill = optical27Grade(fillSample, faceGain, vec3(0.008 * backlitStrength));
                vec3 mixedFill = optical27BlurFill(ringShadedGlass, gradedFill,
                        0.17 * backlitStrength, 0.12 * backlitStrength, 0.20 * backlitStrength);
                ringShadedGlass = mix(ringShadedGlass, mixedFill, 0.45 * blurBlend * backlitStrength);
            }
        }

        float fillDepth = max(-surfaceDistance, 0.0);
        float keyMask = 1.0 - smoothstep(0.0, max(3.0, backlitWidthPx * 4.0), fillDepth);
        if (keyMask > 0.001) {
            vec2 keyNormal = normalize(os4SdfGradient + vec2(0.00001));
            float keyFacing = dot(keyNormal, ubo.p_invariantLightXY);
            float keyFill = opticalOpposingFill(keyFacing, ubo.p_os4LightParams.y, ubo.p_os4LightParams.z);
            float softKnee = keyFill / (1.0 + 0.40 * (1.0 - keyFill));
            ringShadedGlass = clamp(
                    ringShadedGlass + vec3(0.10 * keyMask * softKnee * backlitStrength),
                    vec3(0.0), vec3(1.0));
        }
    }

    if (innerHighlightAlpha > 0.001 && surfaceDistance < 0.0) {
        float bleedDepth = max(-surfaceDistance, 0.0);
        float bleedRangePx = clamp(backlitWidthPx * 2.0 + edgeAa, 2.0, 5.0);
        float bleedBand = 1.0 - smoothstep(0.0, bleedRangePx, bleedDepth);
        vec3 bleedTint = mix(optical27Grade(edgeTransmission, vec3(0.92), vec3(0.015)),
                OPTICAL_SPECULAR_RGB, opticalSpec * 0.40);
        bleedTint *= mix(0.84, 1.0, opticalSpec);
        ringShadedGlass = mix(ringShadedGlass, bleedTint, bleedBand * 0.18 * backlitStrength);
    }

    float ringDepth = max(-surfaceDistance, 0.0);
    float ringRangePx = min(12.0, max(3.0, backlitWidthPx * 3.0 + edgeAa));
    if (backlitStrength > 0.0 && backlitWidthPx > 0.0
            && backlitSideStart < 1.0
            && surfaceDistance < 0.0
            && ringDepth < ringRangePx) {
        float ringRadiusPx = clamp(backlitWidthPx * 0.85 + edgeAa * 0.8, 1.0, 5.0);
        float ringStrokePx = clamp(backlitWidthPx * 0.70, 0.75, 4.0);
        float invRing = 0.70710678 / ringRadiusPx;
        float ringInner = opticalErfcHalf(ringDepth * invRing);
        float ringOuter = opticalErfcHalf((ringDepth + ringStrokePx) * invRing);
        float ringAmount = clamp(
                (ringInner - ringOuter) * 0.58 * backlitStrength * opticalDark,
                0.0, 0.24);
        ringShadedGlass *= 1.0 - ringAmount;

        float hold = opticalErfcHalf((ringDepth - ringStrokePx * 1.5) * invRing);
        float luma = dot(ringShadedGlass, vec3(0.212646, 0.715332, 0.072205));
        ringShadedGlass = mix(ringShadedGlass, vec3(luma),
                clamp(hold * 0.14 * backlitStrength * opticalDark, 0.0, 0.14));
    }

    ringShadedGlass = mix(ringShadedGlass, ubo.p_baseColor.rgb, ubo.p_baseColor.a);

    if (showNormals) {
        vec3 os4DebugNormal = normalize(mix(N, os4EdgeNormal3, os4EdgeMask));
        vec3 os4DebugColor = mix(os4DebugNormal * 0.5 + 0.5, vec3(1.0), os4SoftLight * 0.7);
        float dbgAlpha = clamp(opacity * ubo.uAlpha, 0.0, 1.0);
        fragColor = vec4(os4DebugColor * dbgAlpha, dbgAlpha);
        return;
    }

    float glassAlpha = opacity * clamp(ubo.uAlpha * ubo.p_miscParams.y, 0.0, 1.0);
    float glassAndGlintAlpha = innerHighlightAlpha + glassAlpha * (1.0 - innerHighlightAlpha);
    vec3 glassAndGlintPremul = OPTICAL_SPECULAR_RGB * innerHighlightAlpha
            + ringShadedGlass * glassAlpha * (1.0 - innerHighlightAlpha);

    vec3 outlineRgb = shadeOpticalMeniscus(edgeTransmission, opticalDark, opticalSpec);
    float glassWithOutlineAlpha = outlineAlpha + glassAndGlintAlpha * (1.0 - outlineAlpha);
    vec3 glassWithOutlinePremul = outlineRgb * outlineAlpha + glassAndGlintPremul * (1.0 - outlineAlpha);
    float finalAlpha = glassWithOutlineAlpha + exteriorShadowAlpha * (1.0 - glassWithOutlineAlpha);
    vec3 finalPremul = glassWithOutlinePremul
            + optical27Grade(ubo.p_castShadowColor.rgb, vec3(0.96), vec3(0.0))
            * exteriorShadowAlpha * (1.0 - glassWithOutlineAlpha);
    vec3 finalRgb = finalPremul / max(finalAlpha, 0.0001);
    if (backlitStrength > 0.0 && innerHighlightAlpha > 0.001 && surfaceDistance < 0.0) {
        float glintTauPx = clamp(backlitWidthPx * 0.55 + 0.65, 0.85, 2.0);
        float rimGlint = exp(-abs(surfaceDistance) / glintTauPx);
        float rimGlintGain = clamp(
                0.10 * backlitStrength * rimGlint * (1.0 - opticalDark) * (1.0 - outlineAlpha)
                / max(finalAlpha, 0.25), 0.0, 0.20);
        finalRgb = min(vec3(1.0), finalRgb + vec3(rimGlintGain));
    }
    fragColor = vec4(clamp(finalRgb, 0.0, 1.0) * finalAlpha, finalAlpha);
}
