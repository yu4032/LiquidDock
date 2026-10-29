package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.hellovoid.prismal.PrismalContinuousCorners;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single-view native advanced material on popup/menu background owners.
 * The original OEM View tree draws its content; no overlay Surface/TextureView is inserted.
 * Ownership is released only for blur we created, never for OEM-owned blur.
 */
final class NativeAdvancedMaterialSurface {
    private static final Map<View, Lease> LEASES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NativeAdvancedMaterialSurface() {}

    static void bind(View target, boolean continuous) {
        if (target == null) return;
        Lease existing = LEASES.get(target);
        if (existing != null) {
            existing.refresh(target);
            return;
        }
        if (target.getWidth() <= 0 || target.getHeight() <= 0) {
            // A popup may not have measured yet. One-shot layout rendezvous, not a
            // repost loop that can starve the UI thread before layout.
            target.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view,
                        int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right <= left || bottom <= top) return;
                    view.removeOnLayoutChangeListener(this);
                    bind(view, continuous);
                }
            });
            return;
        }
        Lease state = new Lease(target, continuous);
        if (!state.acquire(target)) return;
        LEASES.put(target, state);
    }

    /** Vendor backdrop already owns blur; apply only optional native visual parameters. */
    static void decorateVendor(View view, boolean continuous) {
        if (view == null) return;
        if (continuous) NativeMaterialContinuousCornersHook.applyTo(view);
        NativeAdvancedMaterialStyle.get().applyVendor(view);
    }

    static void release(View target) {
        if (target == null) return;
        Lease existing = LEASES.remove(target);
        if (existing != null) existing.release(target);
    }

    private static final class Lease implements View.OnAttachStateChangeListener {
        final ViewOutlineProvider originalOutline;
        final Drawable originalBackground;
        final ViewOutlineProvider outline;
        final boolean continuous;
        boolean blurOwned;
        int blurRadius;

        Lease(View target, boolean continuous) {
            this.continuous = continuous;
            originalOutline = target.getOutlineProvider();
            originalBackground = target.getBackground();
            outline = new RoundedOutline(continuous, originalOutline);
        }

        boolean acquire(View target) {
            if (target == null || !target.isAttachedToWindow()) return false;
            Boolean vendorBlur = MiBlurBridge.getPassWindowBlurEnabled(target);
            blurOwned = !Boolean.TRUE.equals(vendorBlur);
            if (blurOwned) {
                NativeAdvancedMaterialStyle appearance = NativeAdvancedMaterialStyle.get();
                if (!MiBlurBridge.applyPassWindowBlur(
                        target, appearance.radiusPx, appearance.blendColors())) return false;
                blurRadius = appearance.radiusPx;
                target.setBackground(null);
            }
            if (continuous) {
                target.setOutlineProvider(outline);
                target.invalidateOutline();
            }
            NativeAdvancedMaterialStyle.get().applyVendor(target);
            target.addOnAttachStateChangeListener(this);
            return true;
        }

        void refresh(View target) {
            NativeAdvancedMaterialStyle appearance = NativeAdvancedMaterialStyle.get();
            if (blurOwned && blurRadius != appearance.radiusPx) {
                blurRadius = appearance.radiusPx;
                MiBlurBridge.setPassWindowBlurRadius(target, blurRadius);
            }
            if (blurOwned) appearance.updateOwned(target);
            else appearance.applyVendor(target);
        }

        void release(View target) {
            target.removeOnAttachStateChangeListener(this);
            if (target.getOutlineProvider() == outline) {
                target.setOutlineProvider(originalOutline);
                target.invalidateOutline();
            }
            if (blurOwned) {
                MiBlurBridge.clearPassWindowBlur(target);
                if (target.getBackground() == null) target.setBackground(originalBackground);
            }
        }

        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {
            NativeAdvancedMaterialSurface.release(view);
        }
    }

    private static final class RoundedOutline extends ViewOutlineProvider {
        private final boolean continuous;
        private final ViewOutlineProvider original;
        private final Path path = new Path();
        private int width = -1, height = -1;
        private float lastRadius = Float.NaN;

        RoundedOutline(boolean continuous, ViewOutlineProvider original) {
            this.continuous = continuous;
            this.original = original;
        }

        @Override public void getOutline(View view, Outline out) {
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) { out.setEmpty(); return; }
            float radius = Math.min(w, h) * 0.22f;
            if (original != null) {
                try {
                    Outline nativeOutline = new Outline();
                    original.getOutline(view, nativeOutline);
                    if (nativeOutline.getRadius() > 0f) radius = nativeOutline.getRadius();
                } catch (Throwable ignored) {}
            }
            radius = Math.min(radius, Math.min(w, h) * .5f);
            if (!continuous) { out.setRoundRect(0, 0, w, h, radius); return; }
            if (width != w || height != h || radius != lastRadius) {
                width = w;
                height = h;
                lastRadius = radius;
                path.rewind();
                float[] points = PrismalContinuousCorners.contour(w, h, radius);
                if (points.length < 8) { out.setRoundRect(0, 0, w, h, radius); return; }
                path.moveTo(points[0], points[1]);
                for (int i = 2; i < points.length; i += 2)
                    path.lineTo(points[i], points[i + 1]);
                path.close();
            }
            out.setPath(path);
        }
    }
}
