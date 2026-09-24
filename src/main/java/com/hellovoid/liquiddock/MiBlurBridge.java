package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.graphics.RenderEffect;
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
    private static final Method GET_PASS_WINDOW_BLUR_ENABLED;
    private static final Method GET_MI_BACKGROUND_BLUR_MODE;
    private static final Method GET_MI_BACKGROUND_BLUR_RADIUS;
    private static final Method GET_MI_BACKGROUND_BLEND_COLORS;
    private static final Method GET_PASS_TEXTURE_SCALE;
    private static final Method SET_MI_VIEW_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_RADIUS;
    private static final Method SET_MI_BACKGROUND_BLEND_COLORS;
    private static final Method CLEAR_MI_BACKGROUND_BLEND_COLOR;
    private static final Method SET_BACKDROP_RENDER_EFFECT;
    private static final boolean PASS_BLUR_AVAILABLE;
    private static final boolean BACKDROP_EFFECT_AVAILABLE;

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
        Method passEnabledGetter = null;
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
        Method backgroundModeGetter = null;
        Method backgroundRadiusGetter = null;
        Method backgroundBlendColorsGetter = null;
        Method passTextureScaleGetter = null;
        try {
            passEnabledGetter = View.class.getMethod("getPassWindowBlurEnabled");
            backgroundModeGetter = View.class.getMethod("getMiBackgroundBlurMode");
            backgroundRadiusGetter = View.class.getMethod("getMiBackgroundBlurRadius");
            backgroundBlendColorsGetter = View.class.getMethod("getMiBackgroundBlendColors");
            passTextureScaleGetter = View.class.getMethod("getPassTextureScale");
        } catch (Throwable ignored) {
            // A replacement path may only claim vendor material when the complete original state
            // can be read back and restored exactly.
        }
        SET_PASS_WINDOW_BLUR_ENABLED = passEnabled;
        GET_PASS_WINDOW_BLUR_ENABLED = passEnabledGetter;
        GET_MI_BACKGROUND_BLUR_MODE = backgroundModeGetter;
        GET_MI_BACKGROUND_BLUR_RADIUS = backgroundRadiusGetter;
        GET_MI_BACKGROUND_BLEND_COLORS = backgroundBlendColorsGetter;
        GET_PASS_TEXTURE_SCALE = passTextureScaleGetter;
        SET_MI_VIEW_BLUR_MODE = viewBlurMode;
        SET_MI_BACKGROUND_BLUR_MODE = backgroundMode;
        SET_MI_BACKGROUND_BLUR_RADIUS = backgroundRadius;
        SET_MI_BACKGROUND_BLEND_COLORS = backgroundBlendColors;
        CLEAR_MI_BACKGROUND_BLEND_COLOR = clearBackgroundBlendColor;
        PASS_BLUR_AVAILABLE = passAvailable;

        Method backdropRenderEffect = null;
        boolean backdropEffectAvailable = false;
        try {
            backdropRenderEffect = View.class.getMethod(
                    "setBackdropRenderEffect", RenderEffect.class);
            backdropEffectAvailable = true;
        } catch (Throwable ignored) {
            // Xiaomi HWUI backdrop effects are optional; callers retain the stock material.
        }
        SET_BACKDROP_RENDER_EFFECT = backdropRenderEffect;
        BACKDROP_EFFECT_AVAILABLE = backdropEffectAvailable;
    }

    private MiBlurBridge() {}

    static final class BackdropRenderEffectState {
        final boolean passWindowBlurEnabled;
        final int backgroundBlurMode;
        final int backgroundBlurRadius;
        final float passTextureScale;
        final ArrayList<Point> backgroundBlendColors;

        BackdropRenderEffectState(
                boolean passWindowBlurEnabled,
                int backgroundBlurMode,
                int backgroundBlurRadius,
                float passTextureScale,
                ArrayList<Point> backgroundBlendColors) {
            this.passWindowBlurEnabled = passWindowBlurEnabled;
            this.backgroundBlurMode = backgroundBlurMode;
            this.backgroundBlurRadius = backgroundBlurRadius;
            this.passTextureScale = passTextureScale;
            this.backgroundBlendColors = backgroundBlendColors;
        }
    }

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

    static BackdropRenderEffectState captureBackdropRenderEffectState(View view) {
        if (!PASS_BLUR_AVAILABLE || view == null
                || GET_PASS_WINDOW_BLUR_ENABLED == null
                || GET_MI_BACKGROUND_BLUR_MODE == null
                || GET_MI_BACKGROUND_BLUR_RADIUS == null
                || GET_MI_BACKGROUND_BLEND_COLORS == null
                || GET_PASS_TEXTURE_SCALE == null
                || SET_PASS_TEXTURE_SCALE == null) {
            return null;
        }
        try {
            Object passEnabled = GET_PASS_WINDOW_BLUR_ENABLED.invoke(view);
            Object mode = GET_MI_BACKGROUND_BLUR_MODE.invoke(view);
            Object radius = GET_MI_BACKGROUND_BLUR_RADIUS.invoke(view);
            Object scale = GET_PASS_TEXTURE_SCALE.invoke(view);
            Object blendColors = GET_MI_BACKGROUND_BLEND_COLORS.invoke(view);
            if (!(passEnabled instanceof Boolean)
                    || !(mode instanceof Number)
                    || !(radius instanceof Number)
                    || !(scale instanceof Number)
                    || !(blendColors instanceof ArrayList)) {
                return null;
            }
            ArrayList<Point> colors = new ArrayList<>();
            for (Object entry : (ArrayList<?>) blendColors) {
                if (!(entry instanceof Point)) return null;
                Point point = (Point) entry;
                colors.add(new Point(point.x, point.y));
            }
            return new BackdropRenderEffectState(
                    (Boolean) passEnabled,
                    ((Number) mode).intValue(),
                    ((Number) radius).intValue(),
                    ((Number) scale).floatValue(),
                    colors);
        } catch (Throwable error) {
            MainHook.log("[DC] backdrop material state capture failed: " + error);
            return null;
        }
    }

    /**
     * Read only the compositor pass-window gate. HyperOS MiuiBlurUtils uses this exact hidden
     * View API, so callers can pause and later restore vendor material without clearing its
     * radius/blend/material configuration.
     */
    static Boolean getPassWindowBlurEnabled(View view) {
        if (GET_PASS_WINDOW_BLUR_ENABLED == null || view == null) return null;
        try {
            Object result = GET_PASS_WINDOW_BLUR_ENABLED.invoke(view);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Throwable error) {
            MainHook.log("[DC] pass window blur state read failed: " + error);
            return null;
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
     * Apply ShortcutMenu optics inside the target RenderNode. This is intentionally different from
     * the generic TextureView PassBlur export: the effect remains part of HWUI composition and
     * therefore cannot become a new SurfaceFlinger layer that feeds back into the next backdrop.
     */
    static boolean applyBackdropRenderEffect(
            View view, RenderEffect effect, int radiusPx, float textureScale) {
        if (!PASS_BLUR_AVAILABLE || !BACKDROP_EFFECT_AVAILABLE
                || view == null || effect == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        float safeScale = Math.max(0.05f, Math.min(1f, textureScale));
        try {
            // Mirrors Launcher BlurUtilities.setContainerBlur(..., mode=2, passWindow=true).
            SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 2);
            SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view);
            if (SET_PASS_TEXTURE_SCALE != null) {
                SET_PASS_TEXTURE_SCALE.invoke(view, safeScale);
            }
            SET_BACKDROP_RENDER_EFFECT.invoke(view, effect);
            view.invalidate();
            return true;
        } catch (Throwable error) {
            // The caller owns the pre-claim snapshot and performs the symmetric rollback. Do not
            // clear unknown vendor state here after a partially successful reflective sequence.
            MainHook.log("[DC] HWUI backdrop effect unavailable: " + error);
            return false;
        }
    }

    static void restoreBackdropRenderEffect(
            View view, BackdropRenderEffectState state) {
        if (view == null || state == null) return;

        if (SET_BACKDROP_RENDER_EFFECT != null) {
            try {
                SET_BACKDROP_RENDER_EFFECT.invoke(view, new Object[]{null});
            } catch (Throwable ignored) {}
        }

        if (!state.passWindowBlurEnabled) {
            try {
                SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, false);
            } catch (Throwable ignored) {}
        }
        try {
            SET_MI_BACKGROUND_BLUR_MODE.invoke(view, state.backgroundBlurMode);
        } catch (Throwable ignored) {}
        try {
            SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, state.backgroundBlurRadius);
        } catch (Throwable ignored) {}
        try {
            SET_PASS_TEXTURE_SCALE.invoke(view, state.passTextureScale);
        } catch (Throwable ignored) {}
        try {
            if (state.backgroundBlendColors.isEmpty()) {
                CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view);
            } else {
                SET_MI_BACKGROUND_BLEND_COLORS.invoke(
                        view, new ArrayList<>(state.backgroundBlendColors));
            }
        } catch (Throwable ignored) {}
        if (state.passWindowBlurEnabled) {
            try {
                SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            } catch (Throwable ignored) {}
        }
        view.invalidate();
    }

    static void clearBackdropRenderEffect(View view) {
        if (view == null) return;
        if (SET_BACKDROP_RENDER_EFFECT != null) {
            try {
                SET_BACKDROP_RENDER_EFFECT.invoke(view, new Object[]{null});
            } catch (Throwable ignored) {}
        }
        if (SET_PASS_TEXTURE_SCALE != null) {
            try { SET_PASS_TEXTURE_SCALE.invoke(view, 1f); }
            catch (Throwable ignored) {}
        }
        clearPassWindowBlur(view);
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
