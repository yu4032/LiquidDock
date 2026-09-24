package com.hellovoid.liquiddock;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

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
            uniform float2 u_origin;
            uniform float2 u_basisX;
            uniform float2 u_basisY;
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

                // Backdrop RenderEffect runs as an image filter over the framebuffer subset,
                // so frag is in window/image coordinates, not mContentView-local coordinates.
                // Recover local popup coordinates from the current transformed View basis.
                float2 delta = frag - u_origin;
                float det = u_basisX.x * u_basisY.y - u_basisX.y * u_basisY.x;
                if (abs(det) < 0.00001) return half4(0.0);
                float2 localFrag = float2(
                        (delta.x * u_basisY.y - delta.y * u_basisY.x) / det,
                        (u_basisX.x * delta.y - u_basisX.y * delta.x) / det);

                float2 halfSize = size * 0.5;
                float minDim = max(1.0, min(halfSize.x, halfSize.y));
                float radius = clamp(u_radius, 0.0, minDim);
                float2 p = localFrag - halfSize;
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
                float2 localSample = clamp(
                        localFrag + lensOffset + snellOffset,
                        float2(0.0), size - float2(1.0));
                float2 sampleP = u_origin
                        + u_basisX * localSample.x
                        + u_basisY * localSample.y;

                float ca = max(u_chromatic, 0.0) * 0.018 * edge;
                float2 chromaDir = outward * ca;
                float2 chromaWindow =
                        u_basisX * chromaDir.x + u_basisY * chromaDir.y;
                half3 color;
                if (ca < 0.02) {
                    color = u_backdrop.eval(sampleP).rgb;
                } else {
                    half r = u_backdrop.eval(sampleP
                            + chromaWindow * u_dispersionR).r;
                    half g = u_backdrop.eval(sampleP).g;
                    half b = u_backdrop.eval(sampleP
                            - chromaWindow * u_dispersionB).b;
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

    private final View target;
    private final RuntimeShader shader;
    private final Miuix307PrismalMaterial.Params params;
    private final float cornerRadius;
    private final View.OnLayoutChangeListener layoutListener;
    private final ViewTreeObserver.OnPreDrawListener preDrawListener;
    private final MiBlurBridge.BackdropRenderEffectState originalBlurState;
    private boolean disposed;

    private ShortcutPopupHwuiGlassEffect(
            View target,
            RuntimeShader shader,
            Miuix307PrismalMaterial.Params params,
            float cornerRadius,
            MiBlurBridge.BackdropRenderEffectState originalBlurState) {
        this.target = target;
        this.shader = shader;
        this.params = params;
        this.cornerRadius = cornerRadius;
        this.originalBlurState = originalBlurState;
        this.layoutListener = (v, left, top, right, bottom,
                               oldLeft, oldTop, oldRight, oldBottom) ->
                updateGeometryAndTransform();
        this.preDrawListener = () -> {
            updateGeometryAndTransform();
            return true;
        };
    }

    static ShortcutPopupHwuiGlassEffect attach(
            View target, LiquidDockConfig.Glass glassConfig, float cornerRadius) {
        if (target == null || !target.isAttachedToWindow()) return null;
        float density = Math.max(
                0.1f, target.getResources().getDisplayMetrics().density);
        Miuix307PrismalMaterial.Params params =
                Miuix307PrismalMaterial.fromConfig(glassConfig, density);
        MiBlurBridge.BackdropRenderEffectState originalBlurState =
                MiBlurBridge.captureBackdropRenderEffectState(target);
        if (originalBlurState == null) {
            MainHook.log(TAG + " reversible vendor blur state unavailable; stock material retained");
            return null;
        }
        ShortcutPopupHwuiGlassEffect binding = null;
        try {
            RuntimeShader shader = new RuntimeShader(AGSL);
            RenderEffect effect =
                    RenderEffect.createRuntimeShaderEffect(shader, BACKDROP);
            binding = new ShortcutPopupHwuiGlassEffect(
                    target,
                    shader,
                    params,
                    Math.max(0f, cornerRadius),
                    originalBlurState);
            if (!MiBlurBridge.applyBackdropRenderEffect(target, effect)) {
                binding.restoreTargetState();
                return null;
            }

            binding.applyStaticUniforms();
            binding.updateGeometryAndTransform();
            target.addOnLayoutChangeListener(binding.layoutListener);
            target.getViewTreeObserver().addOnPreDrawListener(binding.preDrawListener);
            MainHook.log(TAG + " attached target=" + target.getClass().getName()
                    + " size=" + target.getWidth() + "x" + target.getHeight()
                    + " transform=" + binding.describeTransform()
                    + " vendorMode=" + originalBlurState.backgroundBlurMode
                    + " vendorRadius=" + originalBlurState.backgroundBlurRadius
                    + " pass=" + originalBlurState.passWindowBlurEnabled
                    + " blends=" + originalBlurState.backgroundBlendColors.size());
            return binding;
        } catch (Throwable error) {
            if (binding != null) {
                binding.restoreTargetState();
            } else {
                MiBlurBridge.restoreBackdropRenderEffect(target, originalBlurState);
            }
            MainHook.log(TAG + " unavailable; stock material retained: " + error);
            return null;
        }
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        restoreTargetState();
        MainHook.log(TAG + " detached");
    }

    private void restoreTargetState() {
        try {
            target.removeOnLayoutChangeListener(layoutListener);
        } catch (Throwable ignored) {}
        try {
            ViewTreeObserver observer = target.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(preDrawListener);
        } catch (Throwable ignored) {}
        MiBlurBridge.restoreBackdropRenderEffect(target, originalBlurState);
        target.invalidate();
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

    private final int[] origin = new int[2];
    private final float[] localOrigin = new float[2];
    private final float[] localXAxis = new float[2];
    private final float[] localYAxis = new float[2];
    private float basisXx = 1f;
    private float basisXy;
    private float basisYx;
    private float basisYy = 1f;

    private void updateGeometryAndTransform() {
        if (disposed) return;
        int width = Math.max(1, target.getWidth());
        int height = Math.max(1, target.getHeight());

        target.getLocationInSurface(origin);

        localOrigin[0] = 0f;
        localOrigin[1] = 0f;
        localXAxis[0] = width;
        localXAxis[1] = 0f;
        localYAxis[0] = 0f;
        localYAxis[1] = height;
        mapThroughViewParents(target, localOrigin);
        mapThroughViewParents(target, localXAxis);
        mapThroughViewParents(target, localYAxis);

        basisXx = (localXAxis[0] - localOrigin[0]) / (float) width;
        basisXy = (localXAxis[1] - localOrigin[1]) / (float) width;
        basisYx = (localYAxis[0] - localOrigin[0]) / (float) height;
        basisYy = (localYAxis[1] - localOrigin[1]) / (float) height;

        shader.setFloatUniform("u_size", (float) width, (float) height);
        shader.setFloatUniform("u_origin", (float) origin[0], (float) origin[1]);
        shader.setFloatUniform("u_basisX", basisXx, basisXy);
        shader.setFloatUniform("u_basisY", basisYx, basisYy);
    }

    private static void mapThroughViewParents(View target, float[] point) {
        if (target == null || point == null || point.length < 2) return;

        View current = target;
        if (!current.getMatrix().isIdentity()) current.getMatrix().mapPoints(point);
        point[0] += current.getLeft();
        point[1] += current.getTop();

        ViewParent parent = current.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            point[0] -= view.getScrollX();
            point[1] -= view.getScrollY();
            if (!view.getMatrix().isIdentity()) view.getMatrix().mapPoints(point);
            point[0] += view.getLeft();
            point[1] += view.getTop();
            parent = view.getParent();
        }
    }

    private String describeTransform() {
        return "origin=" + origin[0] + "," + origin[1]
                + " bx=" + basisXx + "," + basisXy
                + " by=" + basisYx + "," + basisYy;
    }
}
