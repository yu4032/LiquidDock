package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** DragView alone owns drag snapshots. App transitions use the OEM motion mask adapter. */
final class LauncherMovingIconGlassClip {
    private static final Map<View, Clip> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Clip extends ViewOutlineProvider {
        final ViewOutlineProvider previous;
        final boolean previousClip;
        final Path path = new Path();
        float left, top, right, bottom, radius;
        boolean continuous;

        Clip(View view) {
            previous = view.getOutlineProvider();
            previousClip = view.getClipToOutline();
        }

        void update(View view, LauncherIconGlassClipGeometry.Result shape, boolean c) {
            if (left == shape.left && top == shape.top && right == shape.right
                    && bottom == shape.bottom && radius == shape.radius
                    && continuous == c && view.getOutlineProvider() == this) return;
            left = shape.left;
            top = shape.top;
            right = shape.right;
            bottom = shape.bottom;
            radius = shape.radius;
            continuous = c;
            if (view.getOutlineProvider() != this) view.setOutlineProvider(this);
            if (!view.getClipToOutline()) view.setClipToOutline(true);
            view.invalidateOutline();
        }

        @Override public void getOutline(View view, Outline outline) {
            if (continuous) {
                LauncherIconGlassShapePath.build(path, left, top, right, bottom, radius, true);
                outline.setPath(path);
            } else {
                outline.setRoundRect(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom), radius);
            }
            outline.setAlpha(1f);
        }

        void restore(View view) {
            if (view.getOutlineProvider() != this) return;
            view.setOutlineProvider(previous);
            view.setClipToOutline(previousClip);
            view.invalidateOutline();
        }
    }

    private LauncherMovingIconGlassClip() {}

    static void bindDrag(View drag, float[] bounds, GlassComponentStyle style,
                         LiquidDockConfig.Glass config, float nativeRadius) {
        if (drag == null || bounds == null || bounds.length != 4
                || style == null || !style.enabled || config == null
                || !GlassRuntimeState.isIconEnabled()
                || drag.getWidth() <= 0 || drag.getHeight() <= 0) return;
        float density = drag.getResources().getDisplayMetrics().density;
        float expanded = style.sizeOffsetDp * density;
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density : nativeRadius;
        LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                bounds[0], bounds[1], bounds[2], bounds[3],
                expanded, radius, nativeRadius);
        Clip clip = ACTIVE.get(drag);
        if (clip == null) {
            clip = new Clip(drag);
            ACTIVE.put(drag, clip);
        }
        clip.update(drag, shape, config.continuousCorners);
    }

    static void release(View view) {
        if (view == null) return;
        Clip clip = ACTIVE.remove(view);
        if (clip != null) clip.restore(view);
    }

    static void releaseAll() {
        for (View view : new ArrayList<>(ACTIVE.keySet())) release(view);
    }
}
