package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/** Drawable/Outline-only icon shape resolver; performs no pixel readback. */
final class LauncherGlassIconShapeResolver {
    private LauncherGlassIconShapeResolver() {}

    static float resolveAutoRadius(Drawable drawable, float width, float height, float fallback) {
        float min = Math.max(1f, Math.min(width, height));
        float safeFallback = LauncherGlassBoundsPolicy.capRadius(fallback, width, height);
        if (drawable == null) return safeFallback;
        try {
            Outline outline = new Outline();
            drawable.getOutline(outline);
            Rect rect = new Rect();
            if (outline.getRect(rect)) {
                float radius = outline.getRadius();
                if (Float.isFinite(radius) && radius > 0f && rect.width() > 0 && rect.height() > 0) {
                    float scale = Math.min(width / rect.width(), height / rect.height());
                    return LauncherGlassBoundsPolicy.capRadius(radius * scale, width, height);
                }
            }
            if (drawable instanceof AdaptiveIconDrawable) {
                // Framework adaptive masks commonly expose a rounded-rect/circle Outline. When a
                // particular OEM mask cannot be represented by a radius, keep the conservative
                // rounded-square fallback rather than sampling pixels or adding a stencil path.
                drawable.getOutline(outline);
                if (outline.getRect(rect) && outline.getRadius() > 0f) {
                    float scale = Math.min(width / Math.max(1f, rect.width()),
                            height / Math.max(1f, rect.height()));
                    return LauncherGlassBoundsPolicy.capRadius(
                            outline.getRadius() * scale, width, height);
                }
            }
        } catch (Throwable ignored) {}
        return LauncherGlassBoundsPolicy.capRadius(
                safeFallback > 0f ? safeFallback : min * 0.22f, width, height);
    }
}
