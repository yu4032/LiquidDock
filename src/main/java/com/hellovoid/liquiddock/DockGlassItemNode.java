package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

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

    boolean belongsTo(View dockRoot) {
        View view = viewRef.get();
        if (view == null || dockRoot == null || !view.isAttachedToWindow()) return false;
        View cursor = view;
        while (cursor != null) {
            if (cursor == dockRoot) return true;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    /**
     * Cheap UI-thread fingerprint of geometry relative to the Dock root.
     * The Dock root's own translation/scale is intentionally excluded: the whole TextureView layer
     * follows that transform, so moving the Dock must not rebuild every icon geometry snapshot.
     */
    long uiFingerprint(View dockRoot) {
        View view = viewRef.get();
        if (view == null || dockRoot == null || !view.isAttachedToWindow()) return Long.MIN_VALUE;
        long hash = 0xcbf29ce484222325L;
        View cursor = view;
        while (cursor != null && cursor != dockRoot) {
            hash = mix(hash, System.identityHashCode(cursor));
            hash = mix(hash, cursor.getVisibility());
            hash = mix(hash, cursor.getLeft());
            hash = mix(hash, cursor.getTop());
            hash = mix(hash, cursor.getRight());
            hash = mix(hash, cursor.getBottom());
            hash = mix(hash, cursor.getScrollX());
            hash = mix(hash, cursor.getScrollY());
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getRotation()));
            hash = mix(hash, Float.floatToIntBits(cursor.getPivotX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getPivotY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getAlpha()));
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        if (cursor != dockRoot) return Long.MIN_VALUE;

        // Root visibility/alpha affects whether the item is renderable, but root translation and
        // scale are deliberately omitted because the Dock output layer already follows them.
        hash = mix(hash, dockRoot.getVisibility());
        hash = mix(hash, Float.floatToIntBits(dockRoot.getAlpha()));
        if (kind == LauncherGlassNodeKind.ICON && view instanceof TextView) {
            Drawable[] compound = ((TextView) view).getCompoundDrawables();
            Drawable drawable = compound.length > 1 ? compound[1] : null;
            hash = mix(hash, System.identityHashCode(drawable));
            if (drawable != null) {
                hash = mix(hash, drawable.getBounds().left);
                hash = mix(hash, drawable.getBounds().top);
                hash = mix(hash, drawable.getBounds().right);
                hash = mix(hash, drawable.getBounds().bottom);
            }
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

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
            if (view instanceof TextView) {
                Drawable[] compound = ((TextView) view).getCompoundDrawables();
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
