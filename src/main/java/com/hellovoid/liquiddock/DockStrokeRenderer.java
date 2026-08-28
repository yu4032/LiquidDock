package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared Dock border renderer for Liquid Glass and the native blur Dock.
 *
 * Design constraints:
 *  - no independent overlay View / RenderNode;
 *  - no Path.op();
 *  - no Paint.Style.STROKE for the configurable Dock border;
 *  - the center of the Dock is geometrically excluded from every border draw.
 *
 * A border is built from a validated outer path and inner path. The canvas is
 * clipped to the outer path and then clipOutPath(inner) removes the complete
 * Dock interior. Stroke shadow is deliberately not painted into that interior;
 * glass mode delegates it to the glass host's hardware shadow, while native
 * mode coalesces it with the existing HotSeats native MiShadow in
 * DockNativeShadowBridge so two writes never race on one RenderNode.
 */
final class DockStrokeRenderer {
    private static final float MAX_THICKNESS_FRACTION = 0.20f;
    private static final float MIN_INTERIOR_FRACTION = 0.35f;
    private static final long NATIVE_CONFIG_REFRESH_NS = 1_000_000_000L;
    private static final String NATIVE_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    private static final WeakHashMap<View, StrokeDrawable> INSTALLED =
            new WeakHashMap<>();
    /** Native backgrounds remain known even when stroke is currently disabled. */
    private static final WeakHashMap<View, Float> KNOWN_NATIVE_HOSTS =
            new WeakHashMap<>();

    private static boolean nativeHookInstalled;
    private static volatile LiquidDockConfig.Dock cachedNativeConfig;
    private static volatile long nativeConfigReadNanos;

    private DockStrokeRenderer() {}

    /**
     * Install the same renderer on HotSeatsListContentBlurBackground2.
     * This gives the native MIUI blur Dock a border without adding a child/overlay View.
     */
    static void installNativeHook(ClassLoader classLoader,
                                  LiquidDockConfig.Dock initialConfig) {
        if (nativeHookInstalled) return;
        nativeHookInstalled = true;
        cachedNativeConfig = initialConfig;
        nativeConfigReadNanos = System.nanoTime();

        try {
            HookUtil.hookMethod(classLoader,
                    NATIVE_BACKGROUND_CLASS,
                    "setBackgroundRadius",
                    chain -> {
                        Object result =
                                chain.proceed(chain.getArgs().toArray(new Object[0]));

                        if (MainHook.isWorkstationMode()) return result;

                        View background = (View) chain.getThisObject();
                        float radius = readNativeRadius(background);
                        rememberNativeHost(background, radius);
                        LiquidDockConfig.Dock config = currentNativeConfig();

                        configure(background, config, radius);
                        return result;
                    },
                    float.class);
            MainHook.log("[DC] native blur Dock stroke renderer installed");
        } catch (Throwable error) {
            MainHook.log("[DC] native blur Dock stroke renderer unavailable: " + error);
        }
    }

    /**
     * Remember the actual native background independently of current stroke state. Device logs show
     * this exact View is also the authoritative MiShadow target, so it is a reliable runtime owner.
     */
    static void rememberNativeHost(View host, float radius) {
        if (!isNativeHost(host)) return;
        float safeRadius = Math.max(0f, radius);
        synchronized (KNOWN_NATIVE_HOSTS) {
            KNOWN_NATIVE_HOSTS.put(host, safeRadius);
        }
    }

    private static boolean isNativeHost(View host) {
        return host != null && NATIVE_BACKGROUND_CLASS.equals(host.getClass().getName());
    }

    /**
     * Configure a View's foreground border. For Liquid Glass this View is the glass
     * itself; for native mode it is HotSeatsListContentBlurBackground2.
     */
    static void configure(View host, LiquidDockConfig.Dock config, float radius) {
        configureInternal(host, config, radius, true);
    }

    /** 307 in-place glass owns the visual edge, so do not redraw the vendor foreground below it. */
    static void configureReplacingForeground(
            View host, LiquidDockConfig.Dock config, float radius) {
        configureInternal(host, config, radius, false);
    }

    /** Match the glass clip to the same radius basis used by the configured custom stroke. */
    static float resolveConfiguredRadius(
            View host, LiquidDockConfig.Dock config, float nativeBlurRadius) {
        float radius = Math.max(0f, nativeBlurRadius);
        if (host == null || config == null || !VisualRuntimeState.isDockCustomizationEnabled()) {
            return radius;
        }
        float density = host.getResources().getDisplayMetrics().density;
        float cornerScale = config.cornersDp ? density : 1f;
        return Math.max(0f, radius
                + (config.cornerOffset - config.blurCornerOffset) * cornerScale);
    }

    private static void configureInternal(
            View host, LiquidDockConfig.Dock config, float radius,
            boolean preserveExistingForeground) {
        if (host == null) return;
        if (isNativeHost(host)) rememberNativeHost(host, radius);

        synchronized (INSTALLED) {
            StrokeDrawable installed = INSTALLED.get(host);

            if (config == null || !config.strokeEnabled
                    || !VisualRuntimeState.isDockStrokeEnabled()) {
                if (installed != null && host.getForeground() == installed) {
                    host.setForeground(preserveExistingForeground
                            ? installed.baseForeground() : null);
                }
                applyNativeOuterShadow(host, null);
                // Keep both weak owner registries. Runtime false->true must be able to reattach
                // without waiting for another vendor radius callback.
                return;
            }

            Style style = Style.from(config, host);
            Drawable current = host.getForeground();

            if (installed == null && current instanceof StrokeDrawable) {
                installed = (StrokeDrawable) current;
                INSTALLED.put(host, installed);
            }

            if (installed == null) {
                installed = new StrokeDrawable(
                        preserveExistingForeground ? current : null);
                INSTALLED.put(host, installed);
            } else if (!preserveExistingForeground) {
                installed.setBaseForeground(null);
            }

            installed.setStyle(style);
            installed.setRadius(radius);
            if (host.getForeground() != installed) {
                host.setForeground(installed);
            }
            applyNativeOuterShadow(host, style);
            host.invalidate();
        }
    }

    static void onRuntimeStrokeDisabled() {
        synchronized (INSTALLED) {
            for (Map.Entry<View, StrokeDrawable> entry
                    : new ArrayList<>(INSTALLED.entrySet())) {
                View host = entry.getKey();
                StrokeDrawable installed = entry.getValue();
                if (host == null || installed == null) continue;
                if (host.getForeground() == installed) {
                    host.setForeground(installed.baseForeground());
                }
                applyNativeOuterShadow(host, null);
                host.invalidate();
            }
        }
    }

    static void refreshInstalledFromCurrentConfig() {
        LiquidDockConfig.Dock config;
        try {
            config = LiquidDockConfig.load().dock;
        } catch (Throwable error) {
            return;
        }
        cachedNativeConfig = config;
        nativeConfigReadNanos = System.nanoTime();
        if (!VisualRuntimeState.isDockStrokeEnabled()) {
            onRuntimeStrokeDisabled();
            return;
        }

        synchronized (INSTALLED) {
            for (Map.Entry<View, StrokeDrawable> entry
                    : new ArrayList<>(INSTALLED.entrySet())) {
                View host = entry.getKey();
                StrokeDrawable installed = entry.getValue();
                if (host == null || installed == null) continue;
                Style style = Style.from(config, host);
                installed.setStyle(style);
                if (host.getForeground() != installed) {
                    host.setForeground(installed);
                }
                applyNativeOuterShadow(host, style);
                host.invalidate();
            }
        }

        // A native owner may have been observed while stroke was disabled, so no StrokeDrawable
        // existed yet. Reconfigure those known hosts now instead of waiting for setBackgroundRadius.
        ArrayList<Map.Entry<View, Float>> known;
        synchronized (KNOWN_NATIVE_HOSTS) {
            known = new ArrayList<>(KNOWN_NATIVE_HOSTS.entrySet());
        }
        for (Map.Entry<View, Float> entry : known) {
            View host = entry.getKey();
            if (host == null) continue;
            synchronized (INSTALLED) {
                if (INSTALLED.containsKey(host)) continue;
            }
            configure(host, config, entry.getValue() != null ? entry.getValue() : 0f);
        }
    }

    private static void applyNativeOuterShadow(View host, Style style) {
        if (host == null) return;
        // The native HotSeats background owns exactly one RenderNode MiShadow. The final-boundary
        // bridge merges whole-Dock and stroke-shadow settings there; a second direct write here
        // would race the vendor animation and recreate the original flash/ownership bug.
        if (isNativeHost(host)) return;

        boolean enabled = style != null
                && style.shadowEnabled
                && style.shadowRadiusPx > 0f
                && style.shadowAlpha > 0;
        int color = enabled
                ? Color.argb(Math.max(0, Math.min(255, style.shadowAlpha)), 0, 0, 0)
                : Color.TRANSPARENT;
        float radius = enabled ? style.shadowRadiusPx : 0f;
        try {
            HookUtil.invokeStatic("com.miui.home.launcher.common.MiShadowUtils",
                    "applyViewShadow", host, color, 0f, 0f, radius, 1f);
        } catch (Throwable error) {
            MainHook.log("[DC] native stroke outer shadow unavailable: " + error);
        }
    }

    static void updateRadius(View host, float radius) {
        if (host == null) return;
        if (isNativeHost(host)) rememberNativeHost(host, radius);
        synchronized (INSTALLED) {
            StrokeDrawable drawable = INSTALLED.get(host);
            if (drawable == null && host.getForeground() instanceof StrokeDrawable) {
                drawable = (StrokeDrawable) host.getForeground();
                INSTALLED.put(host, drawable);
            }
            if (drawable != null) drawable.setRadius(radius);
        }
    }

    private static float readNativeRadius(View background) {
        try {
            Object value = HookUtil.getField(background, "mCornerRadius");
            if (value instanceof Number) {
                return Math.max(0f, ((Number) value).floatValue());
            }
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    private static LiquidDockConfig.Dock currentNativeConfig() {
        long now = System.nanoTime();
        if (now - nativeConfigReadNanos < NATIVE_CONFIG_REFRESH_NS) {
            return cachedNativeConfig;
        }
        synchronized (DockStrokeRenderer.class) {
            now = System.nanoTime();
            if (now - nativeConfigReadNanos >= NATIVE_CONFIG_REFRESH_NS) {
                try {
                    cachedNativeConfig = LiquidDockConfig.load().dock;
                } catch (Throwable ignored) {
                }
                nativeConfigReadNanos = now;
            }
            return cachedNativeConfig;
        }
    }

    private static final class Style {
        final boolean squircle;
        final boolean fillDiff;
        final float widthPx;
        final float squircleOffsetPx;
        final float squircleCp;
        final float radiusDeltaPx;
        final int color;
        final boolean shadowEnabled;
        final float shadowRadiusPx;
        final int shadowAlpha;

        Style(boolean squircle,
              boolean fillDiff,
              float widthPx,
              float squircleOffsetPx,
              float squircleCp,
              float radiusDeltaPx,
              int color,
              boolean shadowEnabled,
              float shadowRadiusPx,
              int shadowAlpha) {
            this.squircle = squircle;
            this.fillDiff = fillDiff;
            this.widthPx = widthPx;
            this.squircleOffsetPx = squircleOffsetPx;
            this.squircleCp = squircleCp;
            this.radiusDeltaPx = radiusDeltaPx;
            this.color = color;
            this.shadowEnabled = shadowEnabled && VisualRuntimeState.isStrokeShadowEnabled();
            this.shadowRadiusPx = shadowRadiusPx;
            this.shadowAlpha = shadowAlpha;
        }

        static Style from(LiquidDockConfig.Dock config, View host) {
            float density = host.getResources().getDisplayMetrics().density;
            float dimensionScale = config.dimensionsDp ? density : 1f;
            float cornerScale = config.cornersDp ? density : 1f;

            float width = config.squircle
                    ? config.squircleStrokeWidth
                    : (config.fillDiff
                            ? config.strokeWidth
                            : config.standardStrokeWidth);

            // Preserve the old overlay's visual opacity.
            int legacyBaseAlpha = config.squircle ? 200 : 150;
            int effectiveAlpha = Math.round(
                    legacyBaseAlpha
                            * Math.max(0, Math.min(255, config.strokeAlpha))
                            / 255f);

            // Full customization used:
            // stroke radius = system radius + cornerOffset
            // blur radius   = system radius + blurCornerOffset
            // Liquid-only/native mode simply follows the native radius.
            float radiusDelta = config.enabled && VisualRuntimeState.isDockCustomizationEnabled()
                    ? (config.cornerOffset - config.blurCornerOffset) * cornerScale
                    : 0f;

            return new Style(
                    config.squircle,
                    config.fillDiff,
                    Math.max(0f, width * dimensionScale),
                    config.squircle
                            ? config.squircleStrokeOffset * dimensionScale
                            : 0f,
                    Math.max(0.05f, Math.min(0.95f, config.squircleCp)),
                    radiusDelta,
                    Color.argb(
                            effectiveAlpha,
                            config.strokeR,
                            config.strokeG,
                            config.strokeB),
                    config.strokeShadow,
                    Math.max(0f, config.strokeShadowRadius * dimensionScale),
                    Math.max(0, Math.min(255, config.strokeShadowAlpha)));
        }
    }

    private static final class StrokeDrawable extends Drawable {
        private Drawable baseForeground;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outer = new Path();
        private final Path inner = new Path();
        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();

        private Style style;
        private float radius;
        private boolean geometryDirty = true;
        private boolean geometryValid;
        private int drawableAlpha = 255;
        private ColorFilter colorFilter;

        StrokeDrawable(Drawable baseForeground) {
            this.baseForeground = baseForeground;
            paint.setStyle(Paint.Style.FILL);
        }

        Drawable baseForeground() {
            return baseForeground;
        }

        void setBaseForeground(Drawable baseForeground) {
            this.baseForeground = baseForeground;
            invalidateSelf();
        }

        void setStyle(Style style) {
            this.style = style;
            geometryDirty = true;
            invalidateSelf();
        }

        void setRadius(float radius) {
            radius = Math.max(0f, radius);
            if (Float.floatToIntBits(this.radius)
                    == Float.floatToIntBits(radius)) {
                return;
            }
            this.radius = radius;
            geometryDirty = true;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            if (baseForeground != null) {
                baseForeground.draw(canvas);
            }

            Style s = style;
            Rect bounds = getBounds();
            if (s == null
                    || s.widthPx <= 0f
                    || bounds.width() <= 2
                    || bounds.height() <= 2) {
                return;
            }

            boolean strokeVisible = Color.alpha(s.color) > 0 && drawableAlpha > 0;
            if (!strokeVisible) return;
            if (!ensureGeometry(s, bounds)) return;

            int alpha = Math.round(
                    Color.alpha(s.color) * drawableAlpha / 255f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColorFilter(colorFilter);
            paint.setColor(Color.argb(
                    alpha,
                    Color.red(s.color),
                    Color.green(s.color),
                    Color.blue(s.color)));

            // The central Dock body is removed from the clip before any stroke color is drawn.
            int save = canvas.save();
            canvas.clipPath(outer);
            canvas.clipOutPath(inner);
            canvas.drawPath(outer, paint);
            canvas.restoreToCount(save);
        }

        private boolean ensureGeometry(Style s, Rect bounds) {
            if (!geometryDirty) return geometryValid;
            geometryDirty = false;
            geometryValid = false;

            float width = bounds.width();
            float height = bounds.height();
            float minDim = Math.min(width, height);
            float thickness = Math.min(
                    s.widthPx,
                    Math.max(1f, minDim * MAX_THICKNESS_FRACTION));
            if (!(thickness > 0f)) return false;

            float effectiveRadius = Math.max(0f, radius + s.radiusDeltaPx);
            float outerInset;
            float innerInset;
            float outerRadius;
            float innerRadius;
            float innerCp;

            if (s.squircle) {
                outerInset = -s.squircleOffsetPx;
                innerInset = outerInset + thickness;
                outerRadius = Math.max(0f, effectiveRadius + s.squircleOffsetPx);
                innerRadius = Math.max(0f, outerRadius - thickness * 0.5f);
                innerCp = 0.65f;
            } else if (s.fillDiff) {
                outerInset = 0f;
                innerInset = thickness;
                outerRadius = Math.max(0f, effectiveRadius - 1f);
                innerRadius = Math.max(0f, outerRadius - thickness);
                innerCp = s.squircleCp;
            } else {
                float half = thickness * 0.5f;
                outerInset = 1f - half;
                innerInset = 1f + half;
                float centerRadius = Math.max(0f, effectiveRadius - 1f);
                outerRadius = centerRadius + half;
                innerRadius = Math.max(0f, centerRadius - half);
                innerCp = s.squircleCp;
            }

            outerRect.set(bounds.left + outerInset, bounds.top + outerInset,
                    bounds.right - outerInset, bounds.bottom - outerInset);
            innerRect.set(bounds.left + innerInset, bounds.top + innerInset,
                    bounds.right - innerInset, bounds.bottom - innerInset);

            if (outerRect.width() <= 1f || outerRect.height() <= 1f
                    || innerRect.width() <= 1f || innerRect.height() <= 1f
                    || innerRect.width() < width * MIN_INTERIOR_FRACTION
                    || innerRect.height() < height * MIN_INTERIOR_FRACTION) {
                return false;
            }

            outer.rewind();
            inner.rewind();
            buildShape(outer, outerRect, outerRadius, s.squircle, s.squircleCp);
            buildShape(inner, innerRect, innerRadius, s.squircle, innerCp);
            geometryValid = true;
            return true;
        }

        private static void buildShape(
                Path out,
                RectF rect,
                float requestedRadius,
                boolean squircle,
                float cp) {
            float maxRadius =
                    Math.max(0f, Math.min(rect.width(), rect.height()) * 0.5f);
            float r =
                    Math.max(0f, Math.min(requestedRadius, maxRadius));

            if (!squircle || r <= 1f) {
                out.addRoundRect(
                        rect,
                        r,
                        r,
                        Path.Direction.CW);
                return;
            }

            float control =
                    r * Math.max(0.05f, Math.min(0.95f, cp));
            float left = rect.left;
            float top = rect.top;
            float right = rect.right;
            float bottom = rect.bottom;

            out.moveTo(left, top + r);
            out.cubicTo(
                    left,
                    top + r - control,
                    left + r - control,
                    top,
                    left + r,
                    top);
            out.lineTo(right - r, top);
            out.cubicTo(
                    right - r + control,
                    top,
                    right,
                    top + r - control,
                    right,
                    top + r);
            out.lineTo(right, bottom - r);
            out.cubicTo(
                    right,
                    bottom - r + control,
                    right - r + control,
                    bottom,
                    right - r,
                    bottom);
            out.lineTo(left + r, bottom);
            out.cubicTo(
                    left + r - control,
                    bottom,
                    left,
                    bottom - r + control,
                    left,
                    bottom - r);
            out.close();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            geometryDirty = true;
            if (baseForeground != null) {
                baseForeground.setBounds(bounds);
            }
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed =
                    baseForeground != null
                            && baseForeground.setState(state);
            if (changed) invalidateSelf();
            return changed;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean changed =
                    baseForeground != null
                            && baseForeground.setLevel(level);
            if (changed) invalidateSelf();
            return changed;
        }

        @Override
        public boolean isStateful() {
            return baseForeground != null
                    && baseForeground.isStateful();
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            if (baseForeground != null) {
                baseForeground.setAlpha(alpha);
            }
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            if (baseForeground != null) {
                baseForeground.setColorFilter(filter);
            }
            invalidateSelf();
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void jumpToCurrentState() {
            if (baseForeground != null) {
                baseForeground.jumpToCurrentState();
            }
        }
    }
}
