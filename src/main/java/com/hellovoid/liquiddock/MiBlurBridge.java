package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;

/** Cached bridge to HyperOS/MIUI blur entry points used by both legacy and MiuiX docks. */
final class MiBlurBridge {
    private static final int SELF_BLUR_ENHANCE_FLAG = 0x200;

    // Legacy self/content blur.
    private static final Method SET_MI_SELF_BLUR;
    private static final Method SET_PASS_TEXTURE_SCALE;
    private static final Method SET_MI_SELF_BLUR_ENHANCE_FLAG;
    private static final boolean LEGACY_AVAILABLE;

    // Realtime pass-window/background blur used by the MiuiX dock.
    private static final Method SET_PASS_WINDOW_BLUR_ENABLED;
    private static final Method SET_MI_VIEW_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_RADIUS;
    private static final Method SET_MI_BACKGROUND_BLEND_COLORS;
    private static final Method CLEAR_MI_BACKGROUND_BLEND_COLOR;
    private static final boolean PASS_BLUR_AVAILABLE;

    static volatile boolean liquidGlassActive;

    static {
        Method selfBlur = null;
        Method textureScale = null;
        Method enhanceFlag = null;
        boolean legacyAvailable = false;
        try {
            selfBlur = View.class.getMethod("setMiSelfBlur", int.class, ArrayList.class);
            textureScale = View.class.getMethod("setPassTextureScale", float.class);
            enhanceFlag = View.class.getMethod(
                    "setMiSelfBlurEnhanceFlag", int.class, int.class);
            legacyAvailable = true;
        } catch (Throwable ignored) {
            // Fail closed; shader fallback remains available on legacy docks.
        }
        SET_MI_SELF_BLUR = selfBlur;
        SET_PASS_TEXTURE_SCALE = textureScale;
        SET_MI_SELF_BLUR_ENHANCE_FLAG = enhanceFlag;
        LEGACY_AVAILABLE = legacyAvailable;

        Method passEnabled = null;
        Method viewBlurMode = null;
        Method backgroundMode = null;
        Method backgroundRadius = null;
        Method backgroundBlendColors = null;
        Method clearBackgroundBlendColor = null;
        boolean passAvailable = false;
        try {
            passEnabled = View.class.getMethod("setPassWindowBlurEnabled", boolean.class);
            viewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
            backgroundMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
            backgroundRadius = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            backgroundBlendColors = View.class.getMethod(
                    "setMiBackgroundBlendColors", ArrayList.class);
            clearBackgroundBlendColor = View.class.getMethod("clearMiBackgroundBlendColor");
            passAvailable = true;
        } catch (Throwable ignored) {
            // Some older builds expose only self blur. MiuiX caller will fall back cleanly.
        }
        SET_PASS_WINDOW_BLUR_ENABLED = passEnabled;
        SET_MI_VIEW_BLUR_MODE = viewBlurMode;
        SET_MI_BACKGROUND_BLUR_MODE = backgroundMode;
        SET_MI_BACKGROUND_BLUR_RADIUS = backgroundRadius;
        SET_MI_BACKGROUND_BLEND_COLORS = backgroundBlendColors;
        CLEAR_MI_BACKGROUND_BLEND_COLOR = clearBackgroundBlendColor;
        PASS_BLUR_AVAILABLE = passAvailable;
    }

    private MiBlurBridge() {}

    static boolean isAvailable() {
        return LEGACY_AVAILABLE;
    }

    static boolean isPassWindowBlurAvailable() {
        return PASS_BLUR_AVAILABLE;
    }

    static boolean applyContentBlur(View view, int radiusPx, float textureScale) {
        if (!LEGACY_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        float safeScale = Math.max(0.05f, Math.min(1f, textureScale));
        try {
            SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(
                    view, SELF_BLUR_ENHANCE_FLAG, SELF_BLUR_ENHANCE_FLAG);
            SET_MI_SELF_BLUR.invoke(view, safeRadius, null);
            Object result = SET_PASS_TEXTURE_SCALE.invoke(view, safeScale);
            if (result instanceof Boolean && !((Boolean) result)) {
                clearContentBlur(view);
                return false;
            }
            return true;
        } catch (Throwable e) {
            clearContentBlur(view);
            MainHook.log("[DC] advanced material blur unavailable; shader fallback: " + e);
            return false;
        }
    }

    /**
     * Restore only the compositor backdrop radius without replaying the vendor blur mode or
     * pass-window enable state. HyperOS 307 rewrites this radius during HOME/RECENTS transitions.
     */
    static boolean setPassWindowBlurRadius(View view, int radiusPx) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            Object result = SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable e) {
            // Radius repair is deliberately non-destructive: do not disable the vendor material
            // when one repair attempt fails.
            MainHook.log("[DC] pass window blur radius repair failed: " + e);
            return false;
        }
    }

    /** Enable only window pass-through on a parent/root material owner. */
    static boolean setPassWindowBlurEnabled(View view, boolean enabled) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        try {
            Object result = SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, enabled);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable e) {
            MainHook.log("[DC] pass window blur enable failed: " + e);
            return false;
        }
    }

    /** Apply realtime blur to content behind {@code view}; this is not self/content blur. */
    static boolean applyPassWindowBlur(View view, int radiusPx) {
        return applyPassWindowBlur(view, radiusPx, null);
    }

    /** Exact HyperOS background-material path with optional compositor blend colors. */
    static boolean applyPassWindowBlur(
            View view, int radiusPx, ArrayList<Point> blendColors) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 1);
            Object result = SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            SET_MI_VIEW_BLUR_MODE.invoke(view, 1);
            if (blendColors != null) {
                SET_MI_BACKGROUND_BLEND_COLORS.invoke(view, new ArrayList<>(blendColors));
            } else {
                CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view);
            }
            if (result instanceof Boolean && !((Boolean) result)) {
                clearPassWindowBlur(view);
                return false;
            }
            return true;
        } catch (Throwable e) {
            clearPassWindowBlur(view);
            MainHook.log("[DC] pass window blur failed: " + e);
            return false;
        }
    }

    /**
     * Apply the same container protocol used by SystemUI's native advanced clock material.
     * The caller owns scene/clock semantics; this method only maps LiquidDock appearance into
     * stable MIUI View material APIs.
     */
    static boolean applyClockMaterialContainer(View view, int radiusPx) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            Object enabled = SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            if (enabled instanceof Boolean && !((Boolean) enabled)) return false;
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 1);
            SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            if (SET_PASS_TEXTURE_SCALE != null) {
                try { SET_PASS_TEXTURE_SCALE.invoke(view, 0f); } catch (Throwable ignored) {}
            }
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][LockScreenClockGlass] native clock container material failed: " + error);
            return false;
        }
    }

    /**
     * Turn the original clock glyph View itself into the MIUI blur member. No overlay View,
     * bitmap mask, TextureView, Surface or EGL renderer is involved.
     */
    static boolean applyClockMaterialMember(
            View view, int tintR, int tintG, int tintB, int tintAlpha) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        try {
            int a = Math.max(0, Math.min(255, tintAlpha));
            int r = Math.max(0, Math.min(255, tintR));
            int g = Math.max(0, Math.min(255, tintG));
            int b = Math.max(0, Math.min(255, tintB));
            int tint = android.graphics.Color.argb(a, r, g, b);
            ArrayList<Point> blend = new ArrayList<>();
            // These are the same compositor blend slots used by SystemUI MiuiBlurUtils.
            blend.add(new Point(tint, 101));
            blend.add(new Point(android.graphics.Color.argb(0, 0, 0, 0), 103));

            SET_MI_VIEW_BLUR_MODE.invoke(view, 3);
            SET_MI_BACKGROUND_BLEND_COLORS.invoke(view, blend);
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][LockScreenClockGlass] native clock member material failed: " + error);
            return false;
        }
    }

    static void clearPassWindowBlur(View view) {
        if (!PASS_BLUR_AVAILABLE || view == null) return;
        try {
            SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, false);
        } catch (Throwable ignored) {}
        try {
            SET_MI_VIEW_BLUR_MODE.invoke(view, 0);
        } catch (Throwable ignored) {}
        try {
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 0);
        } catch (Throwable ignored) {}
        try {
            SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, 0);
        } catch (Throwable ignored) {}
        try {
            CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view);
        } catch (Throwable ignored) {}
    }

    /** Symmetric cleanup: clear both legacy self blur and MiuiX pass-window blur. */
    static void clearContentBlur(View view) {
        if (view == null) return;
        if (LEGACY_AVAILABLE) {
            try {
                SET_MI_SELF_BLUR.invoke(view, 0, null);
            } catch (Throwable ignored) {}
            try {
                SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(view, 0, SELF_BLUR_ENHANCE_FLAG);
            } catch (Throwable ignored) {}
            try {
                SET_PASS_TEXTURE_SCALE.invoke(view, 1f);
            } catch (Throwable ignored) {}
        }
        clearPassWindowBlur(view);
    }
}
