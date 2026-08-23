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
            hash = mix(hash, System.identityHashCode(cursor));
            hash = mix(hash, cursor.getVisibility());
            hash = mix(hash, cursor.getLeft()); hash = mix(hash, cursor.getTop());
            hash = mix(hash, cursor.getRight()); hash = mix(hash, cursor.getBottom());
            hash = mix(hash, cursor.getScrollX()); hash = mix(hash, cursor.getScrollY());
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getRotation()));
            hash = mix(hash, Float.floatToIntBits(cursor.getAlpha()));
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        hash = mix(hash, dockRoot.getVisibility());
        hash = mix(hash, Float.floatToIntBits(dockRoot.getAlpha()));
        return hash;
    }
    LauncherGlassGeometry.Snapshot capture(View dockRoot, Matrix rootInverse,
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View view = viewRef.get();
        if (view == null || dockRoot == null || rootInverse == null || style == null || !style.enabled
                || !belongsTo(dockRoot) || !LauncherGlassVisibility.isVisible(view, dockRoot)
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
        rootInverse.mapPoints(points);
        float width = Math.max(1f, (points[2] - points[0]) * scaleX);
        float height = Math.max(1f, (points[3] - points[1]) * scaleY);
        float x = sampleInsetLeft + points[0] * scaleX;
        float y = sampleInsetTop + points[1] * scaleY;
        Drawable drawable = null;
        if (view instanceof TextView) {
            Drawable[] drawables = ((TextView) view).getCompoundDrawables();
            if (drawables.length > 1) drawable = drawables[1];
        }
        float fallback = Math.min(width, height) * 0.22f;
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density * Math.min(scaleX, scaleY)
                : LauncherGlassIconShapeResolver.resolveAutoRadius(drawable, width, height, fallback);
        return LauncherGlassGeometry.resolve(framebufferWidth, framebufferHeight,
                x, y, x + width, y + height,
                LauncherGlassBoundsPolicy.capRadius(radius, width, height));
    }
    private static long mix(long h, long v) { return (h ^ v) * 0x100000001b3L; }
}
