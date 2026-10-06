package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import com.hellovoid.prismal.PrismalInteractionState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resolves Gboard SoftKeyView instances into Prismal nodes without relying on numeric resource IDs
 * or obfuscated implementation classes. Native drawable outlines are treated as optional geometry
 * hints only; theme variants may replace them with Ripple/StateList/Inset combinations.
 */
final class GboardSoftKeyGlassScene {
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final float FALLBACK_INSET_X_FRACTION = 0.06f;
    private static final float FALLBACK_INSET_Y_FRACTION = 0.08f;
    private static final float SPECIAL_KEY_FALLBACK_RADIUS_DP = 8f;
    private static final PrismalInteractionState PRESSED =
            new PrismalInteractionState(1f, 0.5f, 0.5f);
    static final Node[] EMPTY = new Node[0];

    private static final WeakHashMap<View, Shape> SHAPE_BY_VIEW = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PREPARED_BY_VIEW = new WeakHashMap<>();

    private static final class Shape {
        final RectF bounds;
        final float radius;

        Shape(RectF bounds, float radius) {
            this.bounds = new RectF(bounds);
            this.radius = Math.max(0f, radius);
        }
    }

    private static final class ShapeTemplate {
        final float leftFraction;
        final float topFraction;
        final float rightFraction;
        final float bottomFraction;

        ShapeTemplate(View source, RectF bounds) {
            float width = Math.max(1f, source.getWidth());
            float height = Math.max(1f, source.getHeight());
            leftFraction = clamp01(bounds.left / width);
            topFraction = clamp01(bounds.top / height);
            rightFraction = clamp01(bounds.right / width);
            bottomFraction = clamp01(bounds.bottom / height);
        }

        RectF apply(View target) {
            float width = Math.max(1f, target.getWidth());
            float height = Math.max(1f, target.getHeight());
            float left = leftFraction * width;
            float top = topFraction * height;
            float right = rightFraction * width;
            float bottom = bottomFraction * height;
            if (right <= left || bottom <= top) return fallbackBounds(target);
            return new RectF(left, top, right, bottom);
        }
    }

    static final class Node {
        final GboardFloatingGlassGeometry geometry;
        final PrismalInteractionState interaction;
        final boolean pressed;

        Node(
                GboardFloatingGlassGeometry geometry,
                PrismalInteractionState interaction,
                boolean pressed) {
            this.geometry = geometry;
            this.interaction = interaction;
            this.pressed = pressed;
        }
    }

    private GboardSoftKeyGlassScene() {}

    static Node[] capture(
            View root,
            View sinkHost,
            GboardFloatingStructureResolver.Structure structure,
            boolean enabled,
            float customCornerRadiusDp) {
        if (root == null || sinkHost == null || structure == null) return EMPTY;

        ArrayList<View> keys = new ArrayList<>();
        for (ViewGroup holder : structure.keyboardViewHolders) {
            collectSoftKeys(holder, keys);
        }
        if (keys.isEmpty()) return EMPTY;

        if (!enabled) {
            synchronized (GboardSoftKeyGlassScene.class) {
                for (View key : keys) PREPARED_BY_VIEW.remove(key);
            }
            return EMPTY;
        }

        Map<View, Shape> nativeShapes = new IdentityHashMap<>();
        ShapeTemplate template = null;
        for (View key : keys) {
            Shape nativeShape = resolveNativeShape(key);
            if (nativeShape == null) continue;
            nativeShapes.put(key, nativeShape);
            if (template == null && !isCustomRadiusExempt(key)) {
                template = new ShapeTemplate(key, nativeShape.bounds);
            }
        }

        ArrayList<Node> nodes = new ArrayList<>(keys.size());
        for (View key : keys) {
            synchronized (GboardSoftKeyGlassScene.class) {
                PREPARED_BY_VIEW.remove(key);
            }
            if (!isDrawableTarget(key)) continue;

            Shape nativeShape = nativeShapes.get(key);
            RectF bounds = nativeShape != null
                    ? nativeShape.bounds
                    : template != null ? template.apply(key) : fallbackBounds(key);
            if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) continue;

            boolean exempt = isCustomRadiusExempt(key);
            float radiusPx;
            if (exempt) {
                radiusPx = nativeShape != null && nativeShape.radius > 0f
                        ? nativeShape.radius : dp(key, SPECIAL_KEY_FALLBACK_RADIUS_DP);
            } else {
                radiusPx = dp(key, Math.max(0f, Math.min(24f, customCornerRadiusDp)));
            }

            GboardFloatingGlassGeometry geometry =
                    GboardFloatingGlassGeometry.captureTargetRect(
                            root, sinkHost, key, bounds, radiusPx);
            if (geometry == null) continue;

            boolean pressed = key.isPressed();
            nodes.add(new Node(
                    geometry,
                    pressed ? PRESSED : PrismalInteractionState.IDLE,
                    pressed));
            synchronized (GboardSoftKeyGlassScene.class) {
                PREPARED_BY_VIEW.put(key, Boolean.TRUE);
            }
        }
        return nodes.isEmpty() ? EMPTY : nodes.toArray(new Node[0]);
    }

    static synchronized boolean isPrepared(View view) {
        return view != null && Boolean.TRUE.equals(PREPARED_BY_VIEW.get(view));
    }

    static boolean isSoftKeyView(View view) {
        if (view == null) return false;
        Class<?> type = view.getClass();
        while (type != null) {
            if (SOFT_KEY_VIEW_CLASS.equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    static synchronized void rememberBackground(View view, Drawable background) {
        if (!isSoftKeyView(view) || background == null) return;
        Shape shape = outlineShape(background, view);
        if (shape != null) SHAPE_BY_VIEW.put(view, shape);
    }

    static boolean sameAs(Node[] first, Node[] second) {
        Node[] a = first != null ? first : EMPTY;
        Node[] b = second != null ? second : EMPTY;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            Node left = a[i];
            Node right = b[i];
            if (left == right) continue;
            if (left == null || right == null
                    || left.pressed != right.pressed
                    || left.geometry == null
                    || !left.geometry.sameAs(right.geometry)) {
                return false;
            }
        }
        return true;
    }

    static boolean isCustomRadiusExempt(View view) {
        String name = resourceEntryName(view);
        if (name == null) return false;
        return name.contains("switch_to_symbol")
                || name.contains("switch_to_non_prime")
                || name.contains("ime_action")
                || name.equals("key_pos_enter");
    }

    private static void collectSoftKeys(View view, List<View> out) {
        if (view == null || out == null) return;
        if (isSoftKeyView(view)) {
            out.add(view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            collectSoftKeys(group.getChildAt(i), out);
        }
    }

    private static boolean isDrawableTarget(View view) {
        return view != null
                && view.isAttachedToWindow()
                && view.getVisibility() == View.VISIBLE
                && view.isShown()
                && view.getWidth() > 0
                && view.getHeight() > 0;
    }

    private static Shape resolveNativeShape(View view) {
        if (view == null) return null;
        Shape shape = outlineShape(view.getBackground(), view);
        if (shape == null) shape = outlineShape(view);
        synchronized (GboardSoftKeyGlassScene.class) {
            if (shape != null) {
                SHAPE_BY_VIEW.put(view, shape);
                return shape;
            }
            return SHAPE_BY_VIEW.get(view);
        }
    }

    private static Shape outlineShape(View view) {
        if (view == null) return null;
        try {
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider == null) return null;
            Outline outline = new Outline();
            provider.getOutline(view, outline);
            return shapeFromOutline(outline, view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Shape outlineShape(Drawable drawable, View view) {
        if (drawable == null || view == null || isTransparentPlaceholder(drawable)) return null;
        try {
            Outline outline = new Outline();
            drawable.getOutline(outline);
            return shapeFromOutline(outline, view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Shape shapeFromOutline(Outline outline, View view) {
        if (outline == null || view == null) return null;
        Rect rect = new Rect();
        float radius = Math.max(0f, outline.getRadius());
        if (outline.getRect(rect) && rect.width() > 0 && rect.height() > 0) {
            return new Shape(new RectF(rect), radius);
        }
        if (radius <= 0f || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        return new Shape(
                new RectF(0f, 0f, view.getWidth(), view.getHeight()),
                radius);
    }

    private static boolean isTransparentPlaceholder(Drawable drawable) {
        return drawable instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) drawable).getColor()) == 0;
    }

    private static RectF fallbackBounds(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        float insetX = view.getWidth() * FALLBACK_INSET_X_FRACTION;
        float insetY = view.getHeight() * FALLBACK_INSET_Y_FRACTION;
        float left = insetX;
        float top = insetY;
        float right = view.getWidth() - insetX;
        float bottom = view.getHeight() - insetY;
        if (right <= left || bottom <= top) {
            return new RectF(0f, 0f, view.getWidth(), view.getHeight());
        }
        return new RectF(left, top, right, bottom);
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float dp(View view, float dp) {
        if (view == null || view.getResources() == null) return Math.max(0f, dp);
        return Math.max(0f, dp) * view.getResources().getDisplayMetrics().density;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
