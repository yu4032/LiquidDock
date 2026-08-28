package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;

import java.util.WeakHashMap;

/**
 * Owns only the parameter rewrite at HyperOS' real native Dock-shadow boundary.
 *
 * Device logs on HyperOS 3.0 show that every visible Dock shadow update ultimately reaches
 * MiShadowUtils.applyViewShadow(HotSeatsListContentBlurBackground2, ...).  Launcher remains the
 * lifecycle/animation owner; LiquidDock only replaces the final color/Y/radius arguments and
 * deliberately preserves offsetX, dispersion, call timing, and RenderNode ownership.
 */
final class DockNativeShadowBridge {
    private static final String TARGET_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final String MI_SHADOW_UTILS =
            "com.miui.home.launcher.common.MiShadowUtils";

    private static final WeakHashMap<View, Envelope> ENVELOPES = new WeakHashMap<>();
    private static volatile LiquidDockConfig.Dock config;
    private static boolean installed;

    private DockNativeShadowBridge() {}

    static synchronized void install(ClassLoader classLoader, LiquidDockConfig.Dock initialConfig) {
        config = initialConfig;
        if (installed) return;
        installed = true;
        try {
            Class<?> utils = Class.forName(MI_SHADOW_UTILS, false, classLoader);
            HookUtil.hookMethod(utils, "applyViewShadow",
                    new Class<?>[]{View.class, int.class, float.class, float.class, float.class,
                            float.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        rewrite(args);
                        return chain.proceed(args);
                    });
            MainHook.log("[DC][ShadowBridge] device-verified MiShadow boundary hooked");
        } catch (Throwable error) {
            installed = false;
            MainHook.log("[DC][ShadowBridge] native boundary unavailable: " + error);
        }
    }

    static void refreshConfig() {
        try {
            config = LiquidDockConfig.load().dock;
        } catch (Throwable error) {
            MainHook.log("[DC][ShadowBridge] config refresh failed: " + error);
        }
    }

    private static void rewrite(Object[] args) {
        if (args == null || args.length < 6 || !(args[0] instanceof View)) return;
        View target = (View) args[0];
        if (!TARGET_CLASS.equals(target.getClass().getName())) return;

        float vendorRadius = number(args[4]);
        DockStrokeRenderer.rememberNativeHost(target, vendorRadius);

        if (MainHook.isWorkstationMode()) return;
        LiquidDockConfig.Dock dock = config;
        if (dock == null) {
            refreshConfig();
            dock = config;
            if (dock == null) return;
        }

        boolean dockCustomization = VisualRuntimeState.isDockCustomizationEnabled();
        boolean dockShadow = dockCustomization && VisualRuntimeState.isDockShadowEnabled();
        boolean nativeStrokeShadow = VisualRuntimeState.isStrokeShadowEnabled()
                && dock.strokeShadow
                && !MiuixGlassHook.isBoundTo(target);
        if (!dockCustomization && !nativeStrokeShadow) return;

        int vendorColor = args[1] instanceof Number
                ? ((Number) args[1]).intValue() : Color.TRANSPARENT;
        int vendorAlpha = Color.alpha(vendorColor);
        float vendorY = number(args[3]);

        Envelope envelope;
        synchronized (ENVELOPES) {
            envelope = ENVELOPES.get(target);
            if (envelope == null) {
                envelope = new Envelope();
                ENVELOPES.put(target, envelope);
            }
            envelope.observe(vendorAlpha, vendorY, vendorRadius);
        }

        float density = target.getResources().getDisplayMetrics().density;
        float scale = dock.dimensionsDp ? density : 1f;

        int dockAlpha = dockShadow ? clamp255(dock.shadowAlpha) : 0;
        float dockRadius = dockShadow
                ? Math.min(Math.max(0f, dock.shadowRadius * scale),
                        Math.max(0f, dock.shadowSize * scale))
                : 0f;
        float dockY = dockShadow ? dock.shadowY * scale : 0f;

        int strokeAlpha = nativeStrokeShadow ? clamp255(dock.strokeShadowAlpha) : 0;
        float strokeRadius = nativeStrokeShadow
                ? Math.max(0f, dock.strokeShadowRadius * scale) : 0f;

        // A RenderNode exposes one native MiShadow. In non-glass mode both settings therefore
        // coalesce into that one hardware shadow instead of racing two writes on the same View.
        int baseAlpha = compositeAlpha(dockAlpha, strokeAlpha);
        float baseRadius = Math.max(dockRadius, strokeRadius);
        float baseY = dockShadow ? dockY : 0f;

        float alphaFactor = envelope.alphaFactor(vendorAlpha);
        float radiusFactor = envelope.radiusFactor(vendorRadius);
        float yFactor = envelope.yFactor(vendorY);

        int outAlpha = clamp255(Math.round(baseAlpha * alphaFactor));
        float outRadius = Math.max(0f, baseRadius * radiusFactor);
        float outY = baseY == 0f ? 0f : baseY * yFactor;

        args[1] = Color.argb(outAlpha,
                Color.red(vendorColor), Color.green(vendorColor), Color.blue(vendorColor));
        args[3] = outY;
        args[4] = outRadius;

        int signature = signature(dockShadow, nativeStrokeShadow, dock);
        if (envelope.lastLogSignature != signature) {
            envelope.lastLogSignature = signature;
            MainHook.log("[DC][ShadowBridge] native rewrite target="
                    + target.getClass().getSimpleName()
                    + " dock=" + dockShadow + " stroke=" + nativeStrokeShadow
                    + " vendor(a=" + vendorAlpha + ",y=" + vendorY + ",r=" + vendorRadius + ")"
                    + " out(a=" + outAlpha + ",y=" + outY + ",r=" + outRadius + ")");
        }
    }

    private static float number(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int compositeAlpha(int first, int second) {
        first = clamp255(first);
        second = clamp255(second);
        return 255 - Math.round((255 - first) * (255 - second) / 255f);
    }

    private static int signature(boolean dockShadow, boolean strokeShadow, LiquidDockConfig.Dock dock) {
        int result = dockShadow ? 1 : 0;
        result = 31 * result + (strokeShadow ? 1 : 0);
        result = 31 * result + dock.shadowAlpha;
        result = 31 * result + Float.floatToIntBits(dock.shadowRadius);
        result = 31 * result + Float.floatToIntBits(dock.shadowSize);
        result = 31 * result + Float.floatToIntBits(dock.shadowY);
        result = 31 * result + dock.strokeShadowAlpha;
        result = 31 * result + Float.floatToIntBits(dock.strokeShadowRadius);
        return result;
    }

    private static final class Envelope {
        int peakAlpha;
        float baselineRadius = Float.NaN;
        float baselineAbsY = Float.NaN;
        int lastLogSignature = Integer.MIN_VALUE;

        void observe(int alpha, float y, float radius) {
            if (alpha > peakAlpha) peakAlpha = alpha;
            if (radius > 0f && Float.isFinite(radius)
                    && (!Float.isFinite(baselineRadius) || radius < baselineRadius)) {
                baselineRadius = radius;
            }
            float absY = Math.abs(y);
            if (absY > 0f && Float.isFinite(absY)
                    && (!Float.isFinite(baselineAbsY) || absY < baselineAbsY)) {
                baselineAbsY = absY;
            }
        }

        float alphaFactor(int alpha) {
            if (peakAlpha <= 0) return alpha > 0 ? 1f : 0f;
            return clamp(alpha / (float) peakAlpha, 0f, 1f);
        }

        float radiusFactor(float radius) {
            if (!(radius > 0f)) return 0f;
            if (!Float.isFinite(baselineRadius) || !(baselineRadius > 0f)) return 1f;
            return clamp(radius / baselineRadius, 0f, 8f);
        }

        float yFactor(float y) {
            float absY = Math.abs(y);
            if (!(absY > 0f)) return 0f;
            if (!Float.isFinite(baselineAbsY) || !(baselineAbsY > 0f)) return 1f;
            return clamp(absY / baselineAbsY, 0f, 8f);
        }

        private static float clamp(float value, float min, float max) {
            if (!Float.isFinite(value)) return min;
            return Math.max(min, Math.min(max, value));
        }
    }
}
