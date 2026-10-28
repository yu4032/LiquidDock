package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Clips an icon's moving visual owner, not its stationary ShortcutIcon.
 *
 * DragView and FloatingIconView2 can render snapshots outside TextView.onDraw().
 * Their outline is local to the icon artwork; native translation, scale and the
 * return/launch easing then transform the *clipped* artwork together with glass.
 */
final class LauncherMovingIconGlassClip {
    private static final Map<View, Clip> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, WeakReference<View>> FLOATING_CONTENT =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Clip extends ViewOutlineProvider {
        final ViewOutlineProvider previousProvider;
        final boolean previousClipping;
        final Path path = new Path();
        float left, top, right, bottom, radius;
        boolean continuous;

        Clip(View view) {
            previousProvider = view.getOutlineProvider();
            previousClipping = view.getClipToOutline();
        }

        void update(View view, LauncherIconGlassClipGeometry.Result shape,
                    boolean nextContinuous) {
            if (left == shape.left && top == shape.top && right == shape.right
                    && bottom == shape.bottom && radius == shape.radius
                    && continuous == nextContinuous && view.getOutlineProvider() == this) {
                return;
            }
            left = shape.left;
            top = shape.top;
            right = shape.right;
            bottom = shape.bottom;
            radius = shape.radius;
            continuous = nextContinuous;
            if (view.getOutlineProvider() != this) view.setOutlineProvider(this);
            if (!view.getClipToOutline()) view.setClipToOutline(true);
            view.invalidateOutline();
        }

        @Override public void getOutline(View view, Outline outline) {
            if (continuous) {
                LauncherIconGlassShapePath.build(path, left, top, right, bottom,
                        radius, true);
                outline.setPath(path);
            } else {
                outline.setRoundRect(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom), radius);
            }
            outline.setAlpha(1f);
        }

        void restore(View view) {
            // Do not undo a newer outline owner installed by Launcher itself.
            if (view.getOutlineProvider() == this) {
                view.setOutlineProvider(previousProvider);
                view.setClipToOutline(previousClipping);
                view.invalidateOutline();
            }
        }
    }

    private LauncherMovingIconGlassClip() {}

    static void bindDrag(View drag, float[] visualBounds, GlassComponentStyle style,
                         LiquidDockConfig.Glass config) {
        if (drag == null || visualBounds == null || visualBounds.length != 4
                || style == null || !style.enabled || config == null
                || !GlassRuntimeState.isIconEnabled()) return;
        bind(drag, visualBounds, style, config.continuousCorners,
                iconDrawable(drag), 1f);
    }

    /** Only the native *draw-icon* owner is clipped; application screenshots are untouched. */
    static void bindFloating(View proxy, View source, boolean drawIcon) {
        if (proxy == null) return;
        if (!drawIcon || source == null || !GlassRuntimeState.isAnyIconEnabled()) {
            releaseFloating(proxy);
            return;
        }
        LiquidDockConfig.Glass config = LiveGlassConfigState.currentGlass();
        if (config == null) {
            releaseFloating(proxy);
            return;
        }
        View content = findIconContent(proxy, source);
        if (content == null) {
            releaseFloating(proxy);
            return;
        }
        WeakReference<View> previous = FLOATING_CONTENT.get(proxy);
        View old = previous != null ? previous.get() : null;
        if (old != null && old != content) release(old);
        FLOATING_CONTENT.put(proxy, new WeakReference<>(content));

        float density = source.getResources().getDisplayMetrics().density;
        float radiusDp = config.iconStyle.cornerRadiusDp;
        radiusDp = WorkstationDockIconRadiusPolicy.resolve(radiusDp,
                workstationRadiusDp(), density, MainHook.isWorkstationMode());
        GlassComponentStyle style = new GlassComponentStyle(true,
                config.iconStyle.sizeOffsetDp, radiusDp);
        float scale = contentScale(source, content);
        bind(content, new float[]{content.getPaddingLeft(), content.getPaddingTop(),
                        content.getWidth() - content.getPaddingRight(),
                        content.getHeight() - content.getPaddingBottom()},
                style, config.continuousCorners, iconDrawable(source), scale);
    }

    private static float workstationRadiusDp() {
        return LauncherIconGlassClip.workstationRadiusDp();
    }

    private static float contentScale(View source, View content) {
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(source);
        if (icon == null || icon.width() <= 0f || icon.height() <= 0f) return 1f;
        return Math.max(0.01f, Math.min(
                content.getWidth() / icon.width(),
                content.getHeight() / icon.height()));
    }

    private static View findIconContent(View proxy, View source) {
        // Prefer an actual image owner. The outer FloatingIconView2 can be a
        // fullscreen overlay and clipping it would also cut the app screenshot.
        if (proxy instanceof ViewGroup) {
            LauncherGlassIconGeometry.Bounds bounds =
                    LauncherGlassIconGeometry.resolve(source);
            View image = bounds != null
                    ? findImageChild((ViewGroup) proxy, bounds.width(), bounds.height())
                    : null;
            if (image != null) return image;
        }
        LauncherGlassIconGeometry.Bounds sourceIcon = LauncherGlassIconGeometry.resolve(source);
        if (sourceIcon == null || proxy.getWidth() <= 0 || proxy.getHeight() <= 0
                || proxy.getWidth() > sourceIcon.width() * 2.5f
                || proxy.getHeight() > sourceIcon.height() * 2.5f) return null;
        return proxy;
    }

    private static View findImageChild(ViewGroup parent, float iconWidth,
                                       float iconHeight) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView && ((ImageView) child).getDrawable() != null
                    && child.getWidth() > 0 && child.getHeight() > 0
                    && child.getWidth() <= iconWidth * 2.5f
                    && child.getHeight() <= iconHeight * 2.5f) return child;
            if (child instanceof ViewGroup) {
                View found = findImageChild((ViewGroup) child, iconWidth, iconHeight);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void bind(View view, float[] bounds, GlassComponentStyle style,
                             boolean continuous, Drawable drawable, float scale) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
        float density = view.getResources().getDisplayMetrics().density;
        float sizeOffset = style.sizeOffsetDp * density * scale;
        float referenceWidth = bounds[2] - bounds[0];
        float referenceHeight = bounds[3] - bounds[1];
        if (!(referenceWidth > 0f && referenceHeight > 0f)) return;
        float[] expanded = LauncherGlassBoundsPolicy.apply(
                bounds[0], bounds[1], bounds[2], bounds[3], sizeOffset);
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density * scale
                : LauncherGlassIconShapeResolver.resolveAutoRadius(drawable,
                        expanded[2] - expanded[0], expanded[3] - expanded[1],
                        Math.min(expanded[2] - expanded[0], expanded[3] - expanded[1]) * .22f);
        LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                bounds[0], bounds[1], bounds[2], bounds[3],
                sizeOffset, radius, radius);
        Clip clip = ACTIVE.get(view);
        if (clip == null) {
            clip = new Clip(view);
            ACTIVE.put(view, clip);
        }
        clip.update(view, shape, continuous);
    }

    static void releaseFloating(View proxy) {
        if (proxy == null) return;
        WeakReference<View> content = FLOATING_CONTENT.remove(proxy);
        View target = content != null ? content.get() : null;
        if (target != null) release(target);
    }

    static void release(View view) {
        if (view == null) return;
        Clip clip = ACTIVE.remove(view);
        if (clip != null) clip.restore(view);
    }

    static void releaseAll() {
        for (View view : new ArrayList<>(ACTIVE.keySet())) release(view);
        FLOATING_CONTENT.clear();
    }

    private static Drawable iconDrawable(View view) {
        if (!(view instanceof TextView)) return null;
        Drawable[] drawables = ((TextView) view).getCompoundDrawables();
        return drawables != null && drawables.length > 1 ? drawables[1] : null;
    }
}
