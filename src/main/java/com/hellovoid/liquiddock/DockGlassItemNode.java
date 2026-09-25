package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Lightweight Dock-local icon glass node. Owns no TextureView, Surface, or EGLSurface. */
final class DockGlassItemNode {
    private final WeakReference<View> viewRef;
    private final GlassComponentStyle style;
    DockGlassItemNode(View view, GlassComponentStyle style) {
        viewRef = new WeakReference<>(view);
        this.style = style;
    }
    View view() { return viewRef.get(); }
    DockIconAnimationState.Sample animationSample(long nowMs) {
        return DockGlassItemRegistry.animationSample(viewRef.get(), nowMs);
    }
    float animationOpacity(long nowMs) {
        return DockGlassItemRegistry.animationOpacity(viewRef.get(), nowMs);
    }
    boolean isFading() {
        return DockGlassItemRegistry.isFading(viewRef.get());
    }
    boolean belongsTo(View dockRoot) {
        View cursor = viewRef.get();
        if (cursor == null || dockRoot == null || !cursor.isAttachedToWindow()) return false;
        while (cursor != null) {
            if (cursor == dockRoot) return true;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    long uiFingerprint(View dockRoot) {
        View cursor = viewRef.get();
        if (cursor == null || !belongsTo(dockRoot)) return Long.MIN_VALUE;
        long hash = 0xcbf29ce484222325L;
        while (cursor != null && cursor != dockRoot) {
            hash = mixViewGeometry(hash, cursor);
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        // HotSeats itself can resize/recenter when items are added or removed while every child
        // keeps the same local coordinates. captureStatic() uses transformMatrixToGlobal(), so
        // the cache fingerprint must include the ownership root that participates in that matrix.
        return mixViewGeometry(hash, dockRoot);
    }

    LauncherGlassGeometry.Snapshot capture(View ownershipRoot, Matrix outputInverse,
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        return captureStatic(ownershipRoot, outputInverse, framebufferWidth, framebufferHeight,
                sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
    }

    LauncherGlassGeometry.Snapshot captureProxy(float[] proxyRect, Matrix outputInverse,
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View view = viewRef.get();
        if (view == null || proxyRect == null || proxyRect.length != 4 || outputInverse == null
                || style == null || !style.enabled || view.getRootView() == null) return null;
        float proxyWidth = proxyRect[2] - proxyRect[0];
        float proxyHeight = proxyRect[3] - proxyRect[1];
        if (!Float.isFinite(proxyWidth) || !Float.isFinite(proxyHeight)
                || proxyWidth <= 0f || proxyHeight <= 0f) return null;

        // FloatingIconView2/FloatingIconLayer2 publish their icon rectangle in Launcher-root space.
        // Convert that exact vendor rectangle into this TextureView's output-local coordinates.
        float[] points = new float[]{
                proxyRect[0], proxyRect[1], proxyRect[2], proxyRect[1],
                proxyRect[0], proxyRect[3], proxyRect[2], proxyRect[3]};
        Matrix rootToGlobal = new Matrix();
        view.getRootView().transformMatrixToGlobal(rootToGlobal);
        rootToGlobal.mapPoints(points);
        outputInverse.mapPoints(points);

        float left = min4(points[0], points[2], points[4], points[6]);
        float top = min4(points[1], points[3], points[5], points[7]);
        float right = max4(points[0], points[2], points[4], points[6]);
        float bottom = max4(points[1], points[3], points[5], points[7]);
        float width = Math.max(1f, (right - left) * scaleX);
        float height = Math.max(1f, (bottom - top) * scaleY);
        float x = sampleInsetLeft + left * scaleX;
        float y = sampleInsetTop + top * scaleY;

        float density = view.getResources().getDisplayMetrics().density;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
        float referenceWidth = icon != null && icon.width() > 0f ? icon.width()
                : Math.max(1f, view.getWidth());
        float referenceHeight = icon != null && icon.height() > 0f ? icon.height()
                : Math.max(1f, view.getHeight());
        Drawable drawable = iconDrawable(view);
        float radius;
        if (style.cornerRadiusDp > 0f) {
            float radiusScale = Math.max(0.01f, Math.min(
                    proxyWidth / referenceWidth, proxyHeight / referenceHeight));
            radius = style.cornerRadiusDp * density * Math.min(scaleX, scaleY) * radiusScale;
        } else {
            radius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                    drawable, width, height, Math.min(width, height) * 0.22f);
        }
        return LauncherGlassGeometry.resolve(framebufferWidth, framebufferHeight,
                x, y, x + width, y + height,
                LauncherGlassBoundsPolicy.capRadius(radius, width, height));
    }

    private LauncherGlassGeometry.Snapshot captureStatic(View ownershipRoot, Matrix outputInverse,
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View view = viewRef.get();
        if (view == null || ownershipRoot == null || outputInverse == null || style == null || !style.enabled
                || !belongsTo(ownershipRoot) || !LauncherGlassVisibility.isVisible(view, ownershipRoot)
                || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
        float left = icon != null ? icon.left : 0f;
        float top = icon != null ? icon.top : 0f;
        float right = icon != null ? icon.right : view.getWidth();
        float bottom = icon != null ? icon.bottom : view.getHeight();
        float density = view.getResources().getDisplayMetrics().density;
        float[] b = LauncherGlassBoundsPolicy.apply(left, top, right, bottom,
                style.sizeOffsetDp * density);
        float[] points = new float[]{b[0], b[1], b[2], b[3]};
        Matrix global = new Matrix();
        view.transformMatrixToGlobal(global);
        global.mapPoints(points);
        outputInverse.mapPoints(points);
        float width = Math.max(1f, (points[2] - points[0]) * scaleX);
        float height = Math.max(1f, (points[3] - points[1]) * scaleY);
        float x = sampleInsetLeft + points[0] * scaleX;
        float y = sampleInsetTop + points[1] * scaleY;
        Drawable drawable = iconDrawable(view);
        float fallback = Math.min(width, height) * 0.22f;
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density * Math.min(scaleX, scaleY)
                : LauncherGlassIconShapeResolver.resolveAutoRadius(drawable, width, height, fallback);
        return LauncherGlassGeometry.resolve(framebufferWidth, framebufferHeight,
                x, y, x + width, y + height,
                LauncherGlassBoundsPolicy.capRadius(radius, width, height));
    }

    private static long mixViewGeometry(long hash, View view) {
        hash = mix(hash, System.identityHashCode(view));
        hash = mix(hash, view.getVisibility());
        hash = mix(hash, view.getLeft()); hash = mix(hash, view.getTop());
        hash = mix(hash, view.getRight()); hash = mix(hash, view.getBottom());
        hash = mix(hash, view.getScrollX()); hash = mix(hash, view.getScrollY());
        hash = mix(hash, Float.floatToIntBits(view.getTranslationX()));
        hash = mix(hash, Float.floatToIntBits(view.getTranslationY()));
        hash = mix(hash, Float.floatToIntBits(view.getScaleX()));
        hash = mix(hash, Float.floatToIntBits(view.getScaleY()));
        hash = mix(hash, Float.floatToIntBits(view.getPivotX()));
        hash = mix(hash, Float.floatToIntBits(view.getPivotY()));
        hash = mix(hash, Float.floatToIntBits(view.getRotation()));
        hash = mix(hash, Float.floatToIntBits(view.getAlpha()));
        return hash;
    }

    private static Drawable iconDrawable(View view) {
        if (!(view instanceof TextView)) return null;
        Drawable[] drawables = ((TextView) view).getCompoundDrawables();
        return drawables.length > 1 ? drawables[1] : null;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }
    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
    private static long mix(long h, long v) { return (h ^ v) * 0x100000001b3L; }
}
