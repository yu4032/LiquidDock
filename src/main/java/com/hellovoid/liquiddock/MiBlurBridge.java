package com.hellovoid.liquiddock;

import android.graphics.Paint;
import android.graphics.Point;
import android.view.View;
import android.widget.TextView;

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
    private static final Method CHOOSE_BACKGROUND_BLUR_CONTAINER;
    private static final boolean PASS_BLUR_AVAILABLE;

    // HyperOS 3 native glass material path used by lockscreen clock glyphs.
    private static final Method SET_MI_GLASS_BLUR_RADIUS;
    private static final Method SET_MI_VIEW_MATERIAL_TYPE;
    private static final Method SET_MI_GLASS;
    private static final Method SET_MI_CUSTOM_SURFACE_COLOR_TYPE;
    private static final Method SET_MI_GLASS_CLIP;
    private static final Method DISABLE_MI_BACKGROUND_CONTAIN_BELOW;
    private static final Method SET_PAINT_GLASS_EFFECT;
    private static final boolean GLASS_MATERIAL_AVAILABLE;

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
        Method chooseBackgroundBlurContainer = null;
        boolean passAvailable = false;
        try {
            passEnabled = View.class.getMethod("setPassWindowBlurEnabled", boolean.class);
            viewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
            backgroundMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
            backgroundRadius = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            backgroundBlendColors = View.class.getMethod(
                    "setMiBackgroundBlendColors", ArrayList.class);
            clearBackgroundBlendColor = View.class.getMethod("clearMiBackgroundBlendColor");
            chooseBackgroundBlurContainer = View.class.getMethod(
                    "chooseBackgroundBlurContainer", View.class);
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
        CHOOSE_BACKGROUND_BLUR_CONTAINER = chooseBackgroundBlurContainer;
        PASS_BLUR_AVAILABLE = passAvailable;

        Method glassBlurRadius = null;
        Method viewMaterialType = null;
        Method glass = null;
        Method customSurfaceColorType = null;
        Method glassClip = null;
        Method disableContainBelow = null;
        Method paintGlassEffect = null;
        boolean glassAvailable = false;
        try {
            glassBlurRadius = View.class.getMethod(
                    "setMiGlassBlurRadius", int.class, int.class);
            viewMaterialType = View.class.getMethod("setMiViewMaterialType", int.class);
            glass = View.class.getMethod("setMiGlass", float[].class);
            customSurfaceColorType = View.class.getMethod(
                    "setMiCustomSurfaceColorType", int.class);
            glassClip = View.class.getMethod(
                    "setMiGlassClip",
                    float.class, float.class, float.class, float.class);
            disableContainBelow = View.class.getMethod(
                    "disableMiBackgroundContainBelow", boolean.class);
            paintGlassEffect = Paint.class.getMethod("setGlassEffect", boolean.class);
            glassAvailable = true;
        } catch (Throwable ignored) {
            // Glass is an OS3-only extension. Keep all other blur paths independently usable.
        }
        SET_MI_GLASS_BLUR_RADIUS = glassBlurRadius;
        SET_MI_VIEW_MATERIAL_TYPE = viewMaterialType;
        SET_MI_GLASS = glass;
        SET_MI_CUSTOM_SURFACE_COLOR_TYPE = customSurfaceColorType;
        SET_MI_GLASS_CLIP = glassClip;
        DISABLE_MI_BACKGROUND_CONTAIN_BELOW = disableContainBelow;
        SET_PAINT_GLASS_EFFECT = paintGlassEffect;
        GLASS_MATERIAL_AVAILABLE = passAvailable && glassAvailable;
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
        if (!GLASS_MATERIAL_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            Object enabled = SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            if (enabled instanceof Boolean && !((Boolean) enabled)) return false;

            // Decompiled HyperOS 3 MiuiBlurUtils#setGlassBlurContainer:
            // pass-window owner + background mode + dedicated glass radius. This is deliberately
            // not setMiBackgroundBlurRadius(), which is the ordinary blur-mix material.
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 1);
            SET_MI_GLASS_BLUR_RADIUS.invoke(view, safeRadius, safeRadius);
            DISABLE_MI_BACKGROUND_CONTAIN_BELOW.invoke(view, true);
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][LockScreenClockGlass] native glass container failed: " + error);
            return false;
        }
    }

    /** Bind a native clock material member to the exact backdrop container chosen by SystemUI. */
    static boolean chooseClockBackgroundBlurContainer(View member, View container) {
        if (!PASS_BLUR_AVAILABLE || member == null || container == null
                || CHOOSE_BACKGROUND_BLUR_CONTAINER == null) return false;
        try {
            CHOOSE_BACKGROUND_BLUR_CONTAINER.invoke(member, container);
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][LockScreenClockGlass] native clock member/container bind failed: " + error);
            return false;
        }
    }

    /**
     * Turn the original clock glyph View itself into the MIUI blur member. No overlay View,
     * bitmap mask, TextureView, Surface or EGL renderer is involved.
     */
    static boolean applyClockMaterialMember(
            View view, int tintR, int tintG, int tintB, int tintAlpha) {
        if (!GLASS_MATERIAL_AVAILABLE || view == null) return false;
        try {
            int a = Math.max(0, Math.min(255, tintAlpha));
            int r = Math.max(0, Math.min(255, tintR));
            int g = Math.max(0, Math.min(255, tintG));
            int b = Math.max(0, Math.min(255, tintB));
            int tint = android.graphics.Color.argb(a, r, g, b);

            // Native TimeView.kt glassData (42 floats). Keep the compositor/refraction/highlight
            // profile identical to HyperOS and only map LiquidDock's existing tint controls.
            float[] glassData = nativeClockGlassData(r, g, b, a);

            // Decompiled MiuiBlurUtils#setGlassEffectMethod.
            SET_MI_VIEW_MATERIAL_TYPE.invoke(view, 1);
            SET_MI_GLASS.invoke(view, (Object) glassData);
            SET_MI_VIEW_BLUR_MODE.invoke(view, 3);
            SET_MI_CUSTOM_SURFACE_COLOR_TYPE.invoke(view, 16);

            // Native TimeView expands the glyph glass clip by 50 px. MiuiTextGlassView is a
            // TextView rather than TimeView, so use its measured local bounds as the safe clip.
            float right = Math.max(1, view.getWidth()) + 50f;
            float bottom = Math.max(1, view.getHeight()) + 50f;
            SET_MI_GLASS_CLIP.invoke(view, -50f, -50f, right, bottom);

            // TimeView#handleParams enables the hidden glass raster path on its Paint before
            // drawPath(). MiuiTextGlassView draws with the TextView paint, so mirror that state.
            if (view instanceof TextView) {
                Paint paint = ((TextView) view).getPaint();
                SET_PAINT_GLASS_EFFECT.invoke(paint, true);
                paint.setStrokeWidth(glassData[19]);
                paint.setStrokeMiter(glassData[20]);
            }

            // ClockEffectUtils still applies member blend colors for both blur-mix and glass.
            ArrayList<Point> blend = new ArrayList<>();
            blend.add(new Point(tint, 101));
            blend.add(new Point(android.graphics.Color.argb(0, 0, 0, 0), 103));
            SET_MI_BACKGROUND_BLEND_COLORS.invoke(view, blend);
            view.invalidate();
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][LockScreenClockGlass] native glass member failed: " + error);
            return false;
        }
    }

    private static float[] nativeClockGlassData(int r, int g, int b, int a) {
        float[] data = new float[]{
                0.05f, 0.35f, 0.5f, 0.55f, 1.0f, 2.0f, 0.3f, 0.0f, 0.0f, 1.0f,
                0.05f, 1.0f, 1.0f, 1.0f, 0.4f, 0.8f, 0.0f, 1.1f, 1.0f, 30.0f,
                2.0f, 200.0f, 400.0f, 0.3f, 2.0f, -2.0f, 2.0f, -1.0f, 6.0f, 3.0f,
                0.3f, 1.1764705f, 1.33f, 1.0f, 1.0f, 1.0f, 0.0f, 0.8f, 0.82f,
                0.0f, 0.0f, 0.0f
        };
        data[11] = r / 255f;
        data[12] = g / 255f;
        data[13] = b / 255f;
        float alpha = a / 255f;
        data[14] = alpha;
        data[16] = alpha;
        return data;
    }

    static void clearClockGlassContainer(View view) {
        if (!GLASS_MATERIAL_AVAILABLE || view == null) return;
        try { SET_MI_GLASS_BLUR_RADIUS.invoke(view, 0, 0); } catch (Throwable ignored) {}
        try { SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 0); } catch (Throwable ignored) {}
        try { SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, false); } catch (Throwable ignored) {}
    }

    static void clearClockGlassMember(View view) {
        if (!GLASS_MATERIAL_AVAILABLE || view == null) return;
        try { SET_MI_VIEW_MATERIAL_TYPE.invoke(view, 0); } catch (Throwable ignored) {}
        try { SET_MI_GLASS.invoke(view, (Object) new float[42]); } catch (Throwable ignored) {}
        try { SET_MI_VIEW_BLUR_MODE.invoke(view, 0); } catch (Throwable ignored) {}
        try { SET_MI_GLASS_BLUR_RADIUS.invoke(view, 0, 0); } catch (Throwable ignored) {}
        try { SET_MI_CUSTOM_SURFACE_COLOR_TYPE.invoke(view, 4); } catch (Throwable ignored) {}
        if (view instanceof TextView) {
            try { SET_PAINT_GLASS_EFFECT.invoke(((TextView) view).getPaint(), false); }
            catch (Throwable ignored) {}
        }
        try { CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view); } catch (Throwable ignored) {}
        try { view.invalidate(); } catch (Throwable ignored) {}
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
