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
            uniform float u_ior;
            uniform float u_thickness;
            uniform float u_normalStrength;
            uniform float u_displacementScale;
            uniform float u_heightWidth;
            uniform float u_dome;
            uniform float u_chromatic;
            uniform float u_dispersionR;
            uniform float u_dispersionB;
            uniform float u_vibrancy;
            uniform float u_brightness;
            uniform float4 u_tint;
            uniform float2 u_lightDir;
            uniform float u_specular;
            uniform float u_shininess;
            uniform float u_rimStrength;
            uniform float u_caustics;
            uniform float u_transmittance;

            float sdRoundRect(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            float heightFromDist(float d, float width) {
                float t = clamp(-d / max(width, 1.0), 0.0, 1.0);
                return sqrt(max(0.0, 2.0 * t - t * t));
            }

            float2 heightGradient(float2 p, float2 halfSize, float radius, float width) {
                float hx1 = heightFromDist(
                        sdRoundRect(p + float2(1.0, 0.0), halfSize, radius), width);
                float hx0 = heightFromDist(
                        sdRoundRect(p - float2(1.0, 0.0), halfSize, radius), width);
                float hy1 = heightFromDist(
                        sdRoundRect(p + float2(0.0, 1.0), halfSize, radius), width);
                float hy0 = heightFromDist(
                        sdRoundRect(p - float2(0.0, 1.0), halfSize, radius), width);
                return float2((hx1 - hx0) * 0.5, (hy1 - hy0) * 0.5);
            }

            half3 vibrant(half3 c, float amount) {
                if (amount <= 1.001) return c;
                half l = dot(c, half3(0.213, 0.715, 0.072));
                return clamp(mix(half3(l), c, half(amount)), half3(0.0), half3(1.0));
            }

            half4 main(float2 frag) {
                float2 size = max(u_size, float2(1.0));
                float2 halfSize = size * 0.5;
                float minDim = max(1.0, min(halfSize.x, halfSize.y));
                float radius = clamp(u_radius, 0.0, minDim);
                float2 p = frag - halfSize;
                float dist = sdRoundRect(p, halfSize, radius);
                float edgeDist = -dist;
                float alpha = 1.0 - smoothstep(-1.25, 0.0, dist);
                if (alpha <= 0.001) return half4(0.0);

                float dome = clamp(u_dome, 0.0, 2.0);
                float width = min(
                        max(u_heightWidth * (1.0 + 0.42 * dome), 1.0), minDim * 0.98);
                float h = heightFromDist(dist, width);
                float2 gradH = heightGradient(p, halfSize, radius, width);
                float3 n = normalize(float3(
                        -gradH.x * u_normalStrength, -gradH.y * u_normalStrength, 1.0));
                float3 v = float3(0.0, 0.0, 1.0);
                float cosVN = clamp(dot(n, v), 0.0, 1.0);
                float ior = max(u_ior, 1.001);
                float r0 = pow((1.0 - ior) / (1.0 + ior), 2.0);
                float fresnel = r0 + (1.0 - r0) * pow(1.0 - cosVN, 5.0);

                float2 outward = length(p) > 0.001 ? normalize(p) : float2(0.0, 1.0);
                float edge = 1.0 - smoothstep(0.0, width, edgeDist);
                float lensPx = width * (0.34 + 0.32 * dome)
                             * max(abs(u_displacementScale), 0.05);
                float2 lensOffset =
                        outward * (-lensPx * edge * (0.42 + 0.58 * h));
                float2 snellOffset = n.xy * u_thickness * h * 0.11
                                   * u_displacementScale
                                   * (0.55 + 0.45 * (1.0 - fresnel));
                float2 sampleP = clamp(
                        frag + lensOffset + snellOffset, float2(0.0), size - float2(1.0));

                float ca = max(u_chromatic, 0.0) * 0.018 * edge;
                float2 chromaDir = outward * ca;
                half3 color;
                if (ca < 0.02) {
                    color = u_backdrop.eval(sampleP).rgb;
                } else {
                    half r = u_backdrop.eval(clamp(
                            sampleP + chromaDir * u_dispersionR,
                            float2(0.0), size - float2(1.0))).r;
                    half g = u_backdrop.eval(sampleP).g;
                    half b = u_backdrop.eval(clamp(
                            sampleP - chromaDir * u_dispersionB,
                            float2(0.0), size - float2(1.0))).b;
                    color = half3(r, g, b);
                }

                color = vibrant(color, u_vibrancy);
                color *= half(u_brightness);
                color = mix(color, color * half3(u_tint.rgb), half(u_tint.a));

                float2 ld = normalize(u_lightDir + float2(0.0001));
                float3 l = normalize(float3(ld, 1.35));
                float3 hv = normalize(l + v);
                float spec = pow(
                        max(dot(n, hv), 0.0), max(u_shininess, 1.0))
                        * u_specular * (0.28 + 0.72 * h);
                float2 ng = length(gradH) > 0.0001 ? normalize(gradH) : outward;
                float edgeLight = dot(ng, ld);
                float rimBand =
                        smoothstep(max(1.0, minDim * 0.09), 0.0, edgeDist);
                float rim = rimBand * u_rimStrength
                          * (pow(max(edgeLight, 0.0), 3.0) * 0.72
                             + pow(max(-edgeLight, 0.0), 1.2) * 0.22)
                          * (0.35 + 0.65 * fresnel);
                float caustic = pow(
                        max(dot(normalize(float3(
                                gradH * u_normalStrength, 0.48)), l), 0.0), 7.0)
                        * u_caustics * h;
                color += half3(spec + rim) * half3(0.99, 0.995, 1.0);
                color += half3(caustic) * half3(1.0, 0.96, 0.90);

                return half4(
                        color, half(alpha * clamp(u_transmittance, 0.0, 1.0)));
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
        shader.setFloatUniform("u_ior", params.ior);
        shader.setFloatUniform("u_thickness", params.thicknessPx);
        shader.setFloatUniform("u_normalStrength", params.normalStrength);
        shader.setFloatUniform("u_displacementScale", params.displacementScale);
        shader.setFloatUniform("u_heightWidth", params.heightTransitionWidthPx);
        shader.setFloatUniform("u_dome", params.liquidDome);
        shader.setFloatUniform("u_chromatic", params.chromaticAberration);
        shader.setFloatUniform("u_dispersionR", params.dispersionR);
        shader.setFloatUniform("u_dispersionB", params.dispersionB);
        shader.setFloatUniform("u_vibrancy", params.vibrancy);
        shader.setFloatUniform("u_brightness", params.brightness);
        shader.setFloatUniform(
                "u_tint", params.tintR, params.tintG, params.tintB, params.tintA);
        shader.setFloatUniform("u_lightDir", params.lightDirX, params.lightDirY);
        shader.setFloatUniform("u_specular", params.specularStrength);
        shader.setFloatUniform("u_shininess", params.specularSharp);
        shader.setFloatUniform("u_rimStrength", params.rimLight);
        shader.setFloatUniform("u_caustics", params.causticIntensity);
        shader.setFloatUniform("u_transmittance", params.transmittance);
    }

    private void updateGeometry() {
        if (disposed) return;
        shader.setFloatUniform(
                "u_size",
                Math.max(1f, contentView.getWidth()),
                Math.max(1f, contentView.getHeight()));
        glassLayer.invalidate();
    }
}
