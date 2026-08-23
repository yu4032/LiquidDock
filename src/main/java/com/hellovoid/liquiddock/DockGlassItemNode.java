package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;

/** Lightweight Dock-local glass item. Owns no output or producer resource. */
final class DockGlassItemNode {
    private final WeakReference<View> viewRef;
    private final LauncherGlassNodeKind kind;
    private final GlassComponentStyle style;

    DockGlassItemNode(View view, LauncherGlassNodeKind kind, GlassComponentStyle style) {
        viewRef = new WeakReference<>(view);
        this.kind = kind;
        this.style = style;
    }

    View view() { return viewRef.get(); }
    LauncherGlassNodeKind kind() { return kind; }

    LauncherGlassGeometry.Snapshot capture(View dockRoot, Matrix rootInverse,
                                            int framebufferWidth, int framebufferHeight,
                                            float sampleInsetLeft, float sampleInsetTop,
                                            float scaleX, float scaleY) {
        View view = viewRef.get();
        if (view == null || dockRoot == null || rootInverse == null || !style.enabled
                || !LauncherGlassVisibility.isVisible(view, dockRoot)
                || view.getWidth() <= 0 || view.getHeight() <= 0) return null;

        float left = 0f, top = 0f, right = view.getWidth(), bottom = view.getHeight();
        Drawable drawable = null;
        if (kind == LauncherGlassNodeKind.ICON) {
            LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
            if (icon != null) {
                left = icon.left; top = icon.top; right = icon.right; bottom = icon.bottom;
            }
            if (view instanceof android.widget.TextView) {
                Drawable[] compound = ((android.widget.TextView) view).getCompoundDrawables();
                if (compound.length > 1) drawable = compound[1];
            }
        }
        float density = view.getResources().getDisplayMetrics().density;
        float[] bounds = LauncherGlassBoundsPolicy.apply(left, top, right, bottom,
                style.sizeOffsetDp * density);
        float[] points = new float[]{bounds[0], bounds[1], bounds[2], bounds[3]};
        Matrix global = new Matrix();
        view.transformMatrixToGlobal(global);
        global.mapPoints(points);
        rootInverse.mapPoints(points);
        float width = Math.max(1f, (points[2] - points[0]) * scaleX);
        float height = Math.max(1f, (points[3] - points[1]) * scaleY);
        float x = sampleInsetLeft + points[0] * scaleX;
        float y = sampleInsetTop + points[1] * scaleY;
        float fallback = Math.min(width, height) * 0.22f;
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density * Math.min(scaleX, scaleY)
                : LauncherGlassIconShapeResolver.resolveAutoRadius(drawable, width, height, fallback);
        radius = LauncherGlassBoundsPolicy.capRadius(radius, width, height);
        return LauncherGlassGeometry.resolve(framebufferWidth, framebufferHeight,
                x, y, x + width, y + height, radius);
    }
}
