package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.hellovoid.prismal.PrismalContinuousCorners;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Native background on Gboard's already-positioned stock floating-keyboard plate. */
final class GboardNativeAdvancedMaterial {
    private static final Map<View, State> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GboardNativeAdvancedMaterial() {}

    static void bind(GboardFloatingStructureResolver.Structure structure,
                     int blurRadius, boolean continuous) {
        if (structure == null) return;
        View plate = structure.stockBackground;
        if (plate == null || !plate.isAttachedToWindow()) return;
        NativeAdvancedMaterialStyle profile =
                NativeAdvancedMaterialStyle.forScope(ScopedGlassOptics.GBOARD);
        blurRadius = profile.radiusPx;
        State existing = OWNERS.get(plate);
        if (existing != null) {
            if (existing.blurRadius != blurRadius) {
                existing.blurRadius = blurRadius;
                MiBlurBridge.setPassWindowBlurRadius(plate, blurRadius);
            }
            // Existing native material geometry follows View layout automatically.
            // Re-invalidating its outline on every keyboard layout adds compositor work.
            return;
        }
        // Stock mode on some Gboard builds already owns native pass-window blur.
        if (Boolean.TRUE.equals(MiBlurBridge.getPassWindowBlurEnabled(plate))) return;
        State state = new State(plate, radius(plate, structure.keyboardArea), blurRadius, continuous);
        if (!MiBlurBridge.applyPassWindowBlur(
                plate, blurRadius, profile.blendColors())) return;
        OWNERS.put(plate, state);
        plate.setOutlineProvider(state.provider);
        plate.setBackground(null);
        plate.invalidateOutline();
        plate.addOnAttachStateChangeListener(state.attachListener);
    }

    static void onLiveConfigChanged(boolean enabled) {
        for (View plate : new java.util.ArrayList<>(OWNERS.keySet())) {
            if (plate == null) continue;
            if (!enabled) {
                release(plate);
            } else {
                State state = OWNERS.get(plate);
                if (state != null) {
                    NativeAdvancedMaterialStyle style =
                            NativeAdvancedMaterialStyle.forScope(ScopedGlassOptics.GBOARD);
                    if (state.blurRadius != style.radiusPx) {
                        MiBlurBridge.setPassWindowBlurRadius(plate, style.radiusPx);
                        state.blurRadius = style.radiusPx;
                    }
                    style.updateOwned(plate);
                }
            }
        }
    }

    static void release(GboardFloatingStructureResolver.Structure structure) {
        if (structure != null) release(structure.stockBackground);
    }

    private static void release(View plate) {
        if (plate == null) return;
        State state = OWNERS.remove(plate);
        if (state == null) return;
        plate.removeOnAttachStateChangeListener(state.attachListener);
        if (plate.getOutlineProvider() == state.provider) {
            plate.setOutlineProvider(state.originalOutline);
            plate.invalidateOutline();
        }
        if (plate.getBackground() == null) plate.setBackground(state.originalBackground);
        MiBlurBridge.clearPassWindowBlur(plate);
    }

    private static float radius(View plate, View fallback) {
        float r = readRadius(plate);
        if (!(r > 0f)) r = readRadius(fallback);
        if (!(r > 0f)) {
            float density = plate.getResources().getDisplayMetrics().density;
            r = 20f * Math.max(.5f, density);
        }
        return r;
    }

    private static float readRadius(View view) {
        if (view == null) return 0f;
        try {
            Outline outline = new Outline();
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider != null) {
                provider.getOutline(view, outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
            Drawable bg = view.getBackground();
            if (bg != null) {
                bg.getOutline(outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
        } catch (Throwable ignored) {}
        return 0f;
    }

    private static final class State {
        final Drawable originalBackground;
        final ViewOutlineProvider originalOutline;
        final ViewOutlineProvider provider;
        final View.OnAttachStateChangeListener attachListener;
        int blurRadius;

        State(View view, float radius, int blurRadius, boolean continuous) {
            originalBackground = view.getBackground();
            originalOutline = view.getOutlineProvider();
            provider = new RoundedOutline(radius, continuous);
            this.blurRadius = blurRadius;
            attachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View attached) {}
                @Override public void onViewDetachedFromWindow(View detached) { release(detached); }
            };
        }
    }

    private static final class RoundedOutline extends ViewOutlineProvider {
        private final float radius;
        private final boolean continuous;
        private final Rect last = new Rect();
        private final Path path = new Path();

        RoundedOutline(float radius, boolean continuous) {
            this.radius = radius;
            this.continuous = continuous;
        }

        @Override public void getOutline(View view, Outline output) {
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) {
                output.setEmpty();
                return;
            }
            float r = Math.min(radius, Math.min(w, h) * .5f);
            if (!continuous || r <= 1f) {
                output.setRoundRect(0, 0, w, h, r);
                return;
            }
            if (last.width() != w || last.height() != h) {
                last.set(0, 0, w, h);
                path.rewind();
                float[] points = PrismalContinuousCorners.contour(w, h, r);
                if (points.length < 8) {
                    output.setRoundRect(0, 0, w, h, r);
                    return;
                }
                path.moveTo(points[0], points[1]);
                for (int i = 2; i < points.length; i += 2)
                    path.lineTo(points[i], points[i + 1]);
                path.close();
            }
            output.setPath(path);
        }
    }
}
