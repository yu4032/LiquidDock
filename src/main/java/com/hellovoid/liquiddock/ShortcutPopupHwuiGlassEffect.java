package com.hellovoid.liquiddock;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.ViewGroup;

/**
 * ShortcutMenu glass rendered inside the popup RenderNode.
 *
 * <p>Unlike a TextureView output, this never creates a second SurfaceFlinger layer/buffer that can
 * be sampled back into the next PassBlur frame. Xiaomi HWUI owns backdrop acquisition and popup
 * animation; this class only supplies the optical RuntimeShader.</p>
 */
final class ShortcutPopupHwuiGlassEffect {
    private static final String TAG = "[DC][ShortcutPopupHwui]";
    private static final String BACKDROP = "u_backdrop";

    private static final String AGSL = """
            uniform shader u_backdrop;
            uniform float2 u_size;
            uniform float u_radius;

            uniform float u_refractionInset;
            uniform float u_sminSmoothing;
            uniform float u_edgeRefractionFalloff;

            uniform float u_ior;
            uniform float u_thickness;
            uniform float u_normalStrength;
            uniform float u_displacementScale;
            uniform float u_heightWidth;
            uniform float u_dome;
            uniform float u_fresnelReflect;
            uniform float u_lensRefractionPx;
            uniform float u_lensDepthEffect;

            uniform float u_chromatic;
            uniform float u_dispersionR;
            uniform float u_dispersionB;
            uniform float u_vibrancy;
            uniform float u_plainHighlight;

            uniform float u_brightness;
            uniform float4 u_tint;
            uniform float u_highlightWidth;

            uniform float2 u_lightDir;
            uniform float u_specular;
            uniform float u_shininess;
            uniform float u_rimStrength;

            uniform float4 u_shadowColor;
            uniform float u_shadowSoftness;
            uniform float u_caustics;
            uniform float u_transmittance;

            uniform float u_os4EdgeWidthPx;
            uniform float u_os4ReflectOffsetPx;
            uniform float u_os4ReflectionStrength;
            uniform float u_os4ReflectionLighten;
            uniform float u_os4DirectionalAngleRange;
            uniform float u_os4DirectionalIntensity;
            uniform float u_os4DirectionalOppositeIntensity;

            float sminPoly(float a, float b, float k) {
                if (k <= 0.0) return min(a, b);
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            float smaxPoly(float a, float b, float k) {
                if (k <= 0.0) return max(a, b);
                float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
                return mix(b, a, h) + k * h * (1.0 - h);
            }

            float sdRoundBox(float2 p, float2 b, float r, float k) {
                float2 q = abs(p) - b + r;
                if (k <= 0.0) {
                    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
                }
                float a = smaxPoly(q.x, q.y, k);
                float c = sminPoly(a, 0.0, k * 0.5);
                float2 ql = float2(
                        smaxPoly(q.x, 0.0, k),
                        smaxPoly(q.y, 0.0, k));
                return c + length(ql) - r;
            }

            float2 gradRoundRect(float2 p, float2 halfSize, float radius) {
                float2 corner = abs(p) - (halfSize - float2(radius));
                if (corner.x >= 0.0 || corner.y >= 0.0) {
                    float2 m = max(corner, float2(0.0));
                    float len = length(m);
                    return len < 0.00001 ? float2(0.0) : sign(p) * (m / len);
                }
                float gx = step(corner.y, corner.x);
                return sign(p) * float2(gx, 1.0 - gx);
            }

            float circleMap(float x) {
                x = clamp(x, 0.0, 1.0);
                return 1.0 - sqrt(max(0.0, 1.0 - x * x));
            }

            float heightFromDist(float d, float width) {
                float t = clamp(-d / max(width, 1.0), 0.0, 1.0);
                return sqrt(max(0.0, 2.0 * t - t * t));
            }

            float2 heightGradient(
                    float2 p, float2 halfSize, float radius, float smoothing, float width) {
                float hx1 = heightFromDist(
                        sdRoundBox(p + float2(1.0, 0.0), halfSize, radius, smoothing), width);
                float hx0 = heightFromDist(
                        sdRoundBox(p - float2(1.0, 0.0), halfSize, radius, smoothing), width);
                float hy1 = heightFromDist(
                        sdRoundBox(p + float2(0.0, 1.0), halfSize, radius, smoothing), width);
                float hy0 = heightFromDist(
                        sdRoundBox(p - float2(0.0, 1.0), halfSize, radius, smoothing), width);
                return float2((hx1 - hx0) * 0.5, (hy1 - hy0) * 0.5);
            }

            half3 vibrant(half3 c, float amount) {
                if (amount <= 1.001) return c;
                half l = dot(c, half3(0.213, 0.715, 0.072));
                return clamp(mix(half3(l), c, half(amount)), half3(0.0), half3(1.0));
            }

            float edgeCurve(float t) {
                t = clamp(t, 0.0, 1.0);
                if (t >= 0.85) return 1.0;
                float shoulder = clamp(mix(0.5, 1.0, sqrt(t)), 0.0, 1.0);
                float smoother = shoulder * shoulder * shoulder
                        * (shoulder * (shoulder * 6.0 - 15.0) + 10.0);
                float shaped = clamp((smoother - 0.5) * 2.0, 0.0, 1.0);
                return 1.0 - pow(1.0 - shaped, 1.35);
            }

            float edgeBand(float edgeDist, float width) {
                float w = max(width, 1.0);
                float proximity = clamp(1.0 - max(edgeDist, 0.0) / w, 0.0, 1.0);
                return edgeCurve(proximity) * proximity * smoothstep(-1.0, 0.0, edgeDist);
            }

            half4 main(float2 frag) {
                float2 size = max(u_size, float2(1.0));
                float2 halfSize = size * 0.5;
                float minDim = max(1.0, min(halfSize.x, halfSize.y));
                float radius = clamp(u_radius, 0.0, minDim);
                float smoothing = max(u_sminSmoothing, 0.0);
                float2 p = frag - halfSize;

                float dist = sdRoundBox(p, halfSize, radius, smoothing);
                float edgeDist = -dist;
                float inset = min(
                        max(u_refractionInset, 0.8),
                        max(minDim * 0.06, 1.6));
                float alpha = 1.0 - smoothstep(-max(inset * 0.5, 1.0), 0.0, dist);
                if (alpha <= 0.001) return half4(0.0);

                float dome = clamp(u_dome, 0.0, 2.0);
                float refractionHeight = min(
                        max(u_heightWidth * (1.0 + 0.55 * dome), 1.0),
                        minDim * 0.98);
                float tw = min(
                        max(u_heightWidth * (1.0 + 0.38 * dome), 1.0),
                        minDim * 0.98);

                float hSig = heightFromDist(dist, tw);
                float2 gradHSig =
                        heightGradient(p, halfSize, radius, smoothing, tw);
                float2 gradLens = gradRoundRect(
                        p, halfSize, min(radius * 1.5, minDim));

                float innerReach =
                        max(minDim - radius * 0.42, minDim * 0.22)
                        + refractionHeight * (1.0 + 0.25 * dome);
                innerReach = min(innerReach, max(halfSize.x, halfSize.y) * 0.95);
                float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);
                float tShell = 1.0 - tDeep;

                float meniscusBand = smoothstep(0.0, 0.12, tShell);
                float hCap = pow(max(tShell, 0.0), 0.38);
                float edgeBulge = 0.10 * pow(max(tShell, 0.0), 2.8);
                float hDome = (hCap + edgeBulge) * meniscusBand;
                float coreBlend = smoothstep(0.0, 0.38, tDeep);
                float hSlab = mix(
                        hSig * (0.58 + 0.42 * coreBlend),
                        hSig,
                        0.4 + 0.6 * (1.0 - dome));
                float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
                float h = clamp(mix(hSlab, hDome, domeW), 0.0, 1.0);

                float2 outward =
                        length(gradLens) > 0.0001 ? normalize(gradLens)
                                                : float2(0.0, 1.0);
                float shellCurv = smoothstep(0.0, 1.0, tShell);
                float2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));
                gCap *= meniscusBand;
                float2 gradH = mix(gradHSig, gCap, domeW);

                float3 n = normalize(float3(
                        -gradH.x * u_normalStrength,
                        -gradH.y * u_normalStrength,
                        1.0));

                float menW = clamp(edgeDist / tw, 0.0, 1.0);
                float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
                float3 nMeniscus = normalize(float3(
                        -outward * menCirc * 0.95,
                        0.26 + 0.74 * menW));
                float menBlend =
                        smoothstep(tw * 0.42, 0.0, edgeDist)
                        * smoothstep(-4.0, 0.0, dist) * 0.62;
                n = normalize(mix(n, nMeniscus, menBlend));

                float dropLensBase =
                        pow(smoothstep(refractionHeight, 0.0, edgeDist), 0.82);
                float falloffPower =
                        clamp(max(u_edgeRefractionFalloff, 0.05) * 0.205, 0.1, 4.0);
                float dropLens =
                        pow(max(dropLensBase, 0.0), falloffPower / 0.82);

                float3 v = float3(0.0, 0.0, 1.0);
                float cosVN = clamp(dot(n, v), 0.0, 1.0);
                float ior = max(u_ior, 1.001);
                float r0 = pow((1.0 - ior) / (1.0 + ior), 2.0);
                float fresnel = r0 + (1.0 - r0) * pow(1.0 - cosVN, 5.0);
                float fresCtl = clamp(u_fresnelReflect, 0.0, 5.0);
                float fControlled = clamp(fresnel * (0.35 + 0.65 * fresCtl), 0.0, 1.0);

                float2 lensDir = gradLens;
                float lensLen = length(lensDir);
                lensDir = lensLen > 0.00001 ? lensDir / lensLen : float2(0.0);
                float2 centerDir =
                        length(p) > 0.001 ? normalize(p) : float2(0.0, 1.0);
                lensDir = normalize(lensDir + centerDir * u_lensDepthEffect * 0.35
                        + float2(0.00001));

                float sdIn = min(dist, 0.0);
                float dLens = 0.0;
                if ((-dist) < refractionHeight) {
                    dLens = circleMap(
                            1.0 - (-sdIn / refractionHeight))
                            * (-u_lensRefractionPx);
                }

                float2 lensOffset = dLens * lensDir;

                float refrStr = h * (0.5 + fControlled * 0.35);
                float3 refIn = refract(-v, n, 1.0 / ior);
                float3 refOut = dot(refIn, refIn) < 0.001
                        ? float3(0.0)
                        : refract(refIn, -n, ior);
                float2 snellOffset =
                        refOut.xy * u_thickness * refrStr * u_displacementScale;
                snellOffset *=
                        mix(0.72, 1.18, (1.0 - fControlled) * (0.5 + 0.5 * h));

                float bulge = smoothstep(0.05, 0.38, tDeep)
                        * (1.0 - smoothstep(0.52, 0.94, tDeep));
                bulge = pow(max(bulge, 0.0), 0.62) * h
                        * (0.014 + 0.01 * dome);
                float2 bulgeOffset = -centerDir * bulge * size;

                float2 baseOffset =
                        (lensOffset + snellOffset + bulgeOffset) * dropLens;
                float2 sampleP = clamp(
                        frag + baseOffset,
                        float2(0.0), size - float2(1.0));

                float caAmt = max(u_chromatic, 0.0);
                half3 color;
                if (caAmt < 0.02) {
                    color = u_backdrop.eval(sampleP).rgb;
                } else {
                    float edgeFac = pow(
                            smoothstep((size.x + size.y) * 0.25, 0.0, edgeDist), 1.8);
                    float chromaPx = caAmt * 0.0018 * edgeFac * min(size.x, size.y);
                    float2 chromaPush = centerDir * chromaPx;
                    half r = u_backdrop.eval(clamp(
                            sampleP + chromaPush * u_dispersionR,
                            float2(0.0), size - float2(1.0))).r;
                    half g = u_backdrop.eval(sampleP).g;
                    half b = u_backdrop.eval(clamp(
                            sampleP - chromaPush * u_dispersionB,
                            float2(0.0), size - float2(1.0))).b;
                    color = half3(r, g, b);
                }

                color = vibrant(color, u_vibrancy);
                color *= half(u_brightness);
                color = mix(color, color * half3(u_tint.rgb), half(u_tint.a));

                float shadowExt = mix(
                        0.15, 0.60,
                        u_shadowSoftness > 1.0
                                ? clamp(u_shadowSoftness / 20.0, 0.0, 1.0)
                                : clamp(u_shadowSoftness, 0.0, 1.0));
                float shadowFalloff =
                        ((size.x + size.y) * 0.5) * shadowExt;
                float innerShadow =
                        1.0 - smoothstep(0.0, shadowFalloff, edgeDist);
                innerShadow =
                        pow(innerShadow, 2.35) * 0.62 * (0.22 + h * 0.68);
                color = mix(
                        color,
                        half3(u_shadowColor.rgb * 0.25),
                        half(innerShadow * u_shadowColor.a));

                float2 ld = normalize(u_lightDir + float2(0.0001));
                float3 lp = normalize(float3(ld, 1.45));
                float3 ls = normalize(float3(
                        -ld.x * 0.62 + 0.41,
                        -ld.y * 0.62 + 0.33,
                        0.74));
                float3 hp = normalize(lp + v);
                float3 hs = normalize(ls + v);
                float shininess = max(u_shininess, 1.0);

                float specP = pow(max(dot(n, hp), 0.0), shininess)
                        * u_specular * (0.32 + 0.68 * h);
                float specS = pow(max(dot(n, hs), 0.0), shininess * 0.68)
                        * u_specular * 0.48
                        * (0.24 + 0.76 * h)
                        * (0.42 + 0.58 * fControlled);

                float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);
                float rimBand = smoothstep(
                        max(1.0, minDim * 0.09 * opticalEdgeScale),
                        0.0,
                        edgeDist);
                float edgeLight = dot(outward, ld);
                float rim = rimBand * u_rimStrength
                        * (pow(max(edgeLight, 0.0), 3.0) * 0.72
                           + pow(max(-edgeLight, 0.0), 1.2) * 0.22)
                        * (0.35 + 0.65 * fControlled);

                float plusHL = u_plainHighlight * rimBand
                        * (0.18 + 0.82 * pow(max(edgeLight, 0.0), 2.2))
                        * (0.35 + 0.65 * h);

                float os4Width = u_os4EdgeWidthPx > 0.0
                        ? u_os4EdgeWidthPx
                        : clamp(6.0 * opticalEdgeScale, 3.0, 18.0);
                float os4Mask = edgeBand(edgeDist, os4Width);
                float reflectOffset = u_os4ReflectOffsetPx > 0.0
                        ? u_os4ReflectOffsetPx
                        : clamp(u_thickness * 0.38, 4.0, 14.0);
                float2 reflP = clamp(
                        sampleP + outward * reflectOffset
                                * (1.0 - clamp(edgeDist / os4Width, 0.0, 1.0)),
                        float2(0.0), size - float2(1.0));
                half3 reflected = u_backdrop.eval(reflP).rgb;
                float reflWeight = clamp(
                        os4Mask * max(u_os4ReflectionStrength, 0.0),
                        0.0, 1.0);
                color = mix(color, reflected, half(reflWeight));

                float3 edgeN = normalize(float3(outward, 1.0));
                float3 edgeL = normalize(float3(ld, 1.0));
                float mainFacing = max(dot(edgeN, edgeL), 0.0);
                float oppFacing = max(dot(
                        float3(-edgeN.xy, edgeN.z), edgeL), 0.0);
                float angleRange = max(u_os4DirectionalAngleRange, 0.05);
                float mainAngle = acos(clamp(mainFacing, 0.0, 1.0));
                float oppAngle = acos(clamp(oppFacing, 0.0, 1.0));
                float mainFalloff = max(
                        1.0 - mainAngle / (3.14159265 * angleRange), 0.0);
                float oppFalloff = max(
                        1.0 - oppAngle / (3.14159265 * angleRange), 0.0);
                float directional =
                        mainFacing * max(u_os4DirectionalIntensity, 0.0) * mainFalloff
                        + oppFacing
                        * max(u_os4DirectionalOppositeIntensity, 0.0) * oppFalloff;
                float luma = dot(float3(color), float3(0.2126, 0.7152, 0.0722));
                float darkResponse = 1.0 - smoothstep(0.35, 0.92, luma);
                float os4Light = clamp(directional * os4Mask, 0.0, 1.0);
                color += half3(os4Light
                        * (0.42 + max(u_os4ReflectionLighten, 0.0) * darkResponse));

                float caustic = pow(
                        max(dot(
                                normalize(float3(gradH * u_normalStrength, 0.48)),
                                lp), 0.0),
                        7.0) * u_caustics * h;

                color += half3(specP + specS + rim + plusHL)
                        * half3(0.99, 0.995, 1.0);
                color += half3(caustic) * half3(1.0, 0.96, 0.90);

                return half4(
                        color,
                        half(alpha * clamp(u_transmittance, 0.0, 1.0)));
            }
            """;

    private final ViewGroup contentView;
    private final View glassLayer;
    private final RuntimeShader shader;
    private final Miuix307PrismalMaterial.Params params;
    private final float cornerRadius;
    private final View.OnLayoutChangeListener layoutListener;
    private boolean disposed;

    private ShortcutPopupHwuiGlassEffect(
            ViewGroup contentView,
            View glassLayer,
            RuntimeShader shader,
            Miuix307PrismalMaterial.Params params,
            float cornerRadius) {
        this.contentView = contentView;
        this.glassLayer = glassLayer;
        this.shader = shader;
        this.params = params;
        this.cornerRadius = cornerRadius;
        this.layoutListener = (v, left, top, right, bottom,
                               oldLeft, oldTop, oldRight, oldBottom) -> updateGeometry();
    }

    static ShortcutPopupHwuiGlassEffect attach(
            View target, LiquidDockConfig.Glass glassConfig, float cornerRadius) {
        if (!(target instanceof ViewGroup) || !target.isAttachedToWindow()) return null;
        ViewGroup contentView = (ViewGroup) target;
        float density = Math.max(
                0.1f, target.getResources().getDisplayMetrics().density);
        Miuix307PrismalMaterial.Params params =
                Miuix307PrismalMaterial.fromConfig(glassConfig, density);
        MiBlurBridge.BackdropRenderEffectState vendorState =
                MiBlurBridge.captureBackdropRenderEffectState(target);
        if (vendorState == null) {
            MainHook.log(TAG + " vendor blur state unavailable; stock material retained");
            return null;
        }

        int blurRadius = resolveBackdropRadius(target, vendorState, density);
        View glassLayer = new View(target.getContext());
        glassLayer.setClickable(false);
        glassLayer.setFocusable(false);
        glassLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        glassLayer.setSaveEnabled(false);

        ShortcutPopupHwuiGlassEffect binding = null;
        boolean added = false;
        try {
            RuntimeShader shader = new RuntimeShader(AGSL);
            RenderEffect effect =
                    RenderEffect.createRuntimeShaderEffect(shader, BACKDROP);
            binding = new ShortcutPopupHwuiGlassEffect(
                    contentView,
                    glassLayer,
                    shader,
                    params,
                    Math.max(0f, cornerRadius));

            // Put the blur/material plane inside mContentView at index 0. It therefore inherits
            // PopupAnimHelper transforms automatically while all launcher text/icons remain
            // above it and are never processed by the optical RenderEffect.
            contentView.addView(
                    glassLayer,
                    0,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            added = true;

            // The parent already has authoritative non-zero geometry at this point. Establish the
            // MATCH_PARENT child bounds synchronously before registering it as a pass-blur
            // consumer, then let normal parent layout remain authoritative on subsequent frames.
            glassLayer.layout(0, 0, contentView.getWidth(), contentView.getHeight());

            // Xiaomi's pass-window + background/view blur is the real backdrop producer/consumer.
            // The standard RenderEffect is deliberately a *foreground* post-process of this owned
            // background plane; it no longer tries to read Xiaomi's private pass texture as an
            // Android BackdropRenderEffect child shader.
            if (!MiBlurBridge.applyPassWindowBlur(glassLayer, blurRadius)) {
                binding.disposeOwnedLayer();
                return null;
            }
            glassLayer.setRenderEffect(effect);

            binding.applyStaticUniforms();
            binding.updateGeometry();
            contentView.addOnLayoutChangeListener(binding.layoutListener);
            MainHook.log(TAG + " layer attached size="
                    + target.getWidth() + "x" + target.getHeight()
                    + " layerSize=" + glassLayer.getWidth() + "x" + glassLayer.getHeight()
                    + " sourceMode=" + vendorState.backgroundBlurMode
                    + " sourceRadius=" + vendorState.backgroundBlurRadius
                    + " layerRadius=" + blurRadius
                    + " sourcePass=" + vendorState.passWindowBlurEnabled
                    + " sourceViewMode=" + vendorState.viewBlurMode);
            return binding;
        } catch (Throwable error) {
            if (binding != null) {
                binding.disposeOwnedLayer();
            } else if (added) {
                try { contentView.removeView(glassLayer); } catch (Throwable ignored) {}
            }
            MainHook.log(TAG + " layer unavailable; stock material retained: " + error);
            return null;
        }
    }

    private static int resolveBackdropRadius(
            View target, MiBlurBridge.BackdropRenderEffectState state, float density) {
        if (state.backgroundBlurRadius > 0) return state.backgroundBlurRadius;
        try {
            int id = target.getResources().getIdentifier(
                    "shortcut_menu_blur_radius", "dimen", "com.miui.home");
            if (id != 0) {
                int px = target.getResources().getDimensionPixelSize(id);
                if (px > 0) return Math.min(400, px);
            }
        } catch (Throwable ignored) {}
        return Math.min(400, Math.max(1, Math.round(40f * density)));
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        disposeOwnedLayer();
        MainHook.log(TAG + " layer detached");
    }

    private void disposeOwnedLayer() {
        try {
            contentView.removeOnLayoutChangeListener(layoutListener);
        } catch (Throwable ignored) {}
        try {
            glassLayer.setRenderEffect(null);
        } catch (Throwable ignored) {}
        MiBlurBridge.clearPassWindowBlur(glassLayer);
        try {
            if (glassLayer.getParent() == contentView) {
                contentView.removeView(glassLayer);
            }
        } catch (Throwable ignored) {}
        contentView.invalidate();
    }

    private void applyStaticUniforms() {
        shader.setFloatUniform("u_radius", cornerRadius);
        shader.setFloatUniform("u_refractionInset", params.refractionInsetPx);
        shader.setFloatUniform("u_sminSmoothing", params.sminSmoothingPx);
        shader.setFloatUniform("u_edgeRefractionFalloff", params.edgeRefractionFalloff);
        shader.setFloatUniform("u_ior", params.ior);
        shader.setFloatUniform("u_thickness", params.thicknessPx);
        shader.setFloatUniform("u_normalStrength", params.normalStrength);
        shader.setFloatUniform("u_displacementScale", params.displacementScale);
        shader.setFloatUniform("u_heightWidth", params.heightTransitionWidthPx);
        shader.setFloatUniform("u_dome", params.liquidDome);
        shader.setFloatUniform("u_fresnelReflect", params.fresnelReflect);
        shader.setFloatUniform("u_lensDepthEffect", params.lensDepthEffect);
        shader.setFloatUniform("u_chromatic", params.chromaticAberration);
        shader.setFloatUniform("u_dispersionR", params.dispersionR);
        shader.setFloatUniform("u_dispersionB", params.dispersionB);
        shader.setFloatUniform("u_vibrancy", params.vibrancy);
        shader.setFloatUniform("u_plainHighlight", params.plainHighlight);
        shader.setFloatUniform("u_brightness", params.brightness);
        shader.setFloatUniform("u_highlightWidth", params.highlightWidth);
        shader.setFloatUniform(
                "u_tint", params.tintR, params.tintG, params.tintB, params.tintA);
        shader.setFloatUniform("u_lightDir", params.lightDirX, params.lightDirY);
        shader.setFloatUniform("u_specular", params.specularStrength);
        shader.setFloatUniform("u_shininess", params.specularSharp);
        shader.setFloatUniform("u_rimStrength", params.rimLight);
        shader.setFloatUniform(
                "u_shadowColor",
                params.shadowR, params.shadowG, params.shadowB, params.shadowA);
        shader.setFloatUniform("u_shadowSoftness", params.shadowSoftness);
        shader.setFloatUniform("u_caustics", params.causticIntensity);
        shader.setFloatUniform("u_transmittance", params.transmittance);
        shader.setFloatUniform("u_os4EdgeWidthPx", params.os4EdgeWidthPx);
        shader.setFloatUniform("u_os4ReflectOffsetPx", params.os4ReflectOffsetPx);
        shader.setFloatUniform("u_os4ReflectionStrength", params.os4ReflectionStrength);
        shader.setFloatUniform("u_os4ReflectionLighten", params.os4ReflectionLighten);
        shader.setFloatUniform(
                "u_os4DirectionalAngleRange", params.os4DirectionalAngleRange);
        shader.setFloatUniform(
                "u_os4DirectionalIntensity", params.os4DirectionalIntensity);
        shader.setFloatUniform(
                "u_os4DirectionalOppositeIntensity",
                params.os4DirectionalOppositeIntensity);
    }

    private void updateGeometry() {
        if (disposed) return;
        float width = Math.max(1f, contentView.getWidth());
        float height = Math.max(1f, contentView.getHeight());
        shader.setFloatUniform("u_size", width, height);
        float minDim = Math.max(1f, Math.min(width, height));
        float dome = Math.max(0f, Math.min(2f, params.liquidDome));
        float refractionHeight = Math.max(
                params.heightTransitionWidthPx * (1f + 0.55f * dome), 1f);
        float lensPx = refractionHeight * 2f
                * Math.abs(params.displacementScale)
                * Math.abs(params.lensRefractionScale);
        lensPx = Math.max(4f, Math.min(lensPx, Math.max(4f, minDim * 0.85f)));
        shader.setFloatUniform("u_lensRefractionPx", lensPx);
        glassLayer.invalidate();
    }
}
