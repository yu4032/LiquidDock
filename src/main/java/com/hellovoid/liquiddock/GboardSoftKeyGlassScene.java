package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import com.hellovoid.prismal.PrismalInteractionState;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Resolves Gboard SoftKeyView instances into Prismal nodes without relying on resource IDs or
 * obfuscated implementation classes.
 */
final class GboardSoftKeyGlassScene {
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
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
            this.radius = radius;
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
            GboardFloatingStructureResolver.Structure structure) {
        if (root == null || sinkHost == null || structure == null) return EMPTY;

        ArrayList<View> keys = new ArrayList<>();
        for (ViewGroup holder : structure.keyboardViewHolders) {
            collectSoftKeys(holder, keys);
        }
        if (keys.isEmpty()) return EMPTY;

        ArrayList<Node> nodes = new ArrayList<>(keys.size());
        for (View key : keys) {
            synchronized (GboardSoftKeyGlassScene.class) {
                PREPARED_BY_VIEW.remove(key);
            }
            if (!isDrawableTarget(key)) continue;

            Shape shape = resolveShape(key);
            if (shape == null || shape.radius <= 0f) continue;
            GboardFloatingGlassGeometry geometry =
                    GboardFloatingGlassGeometry.captureTargetRect(
                            root, sinkHost, key, shape.bounds, shape.radius);
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

    private static Shape resolveShape(View view) {
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
        if (drawable == null || view == null) return null;
        try {
            Outline outline = new Outline();
            drawable.getOutline(outline);
            return shapeFromOutline(outline, view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Shape shapeFromOutline(Outline outline, View view) {
        if (outline == null || view == null || outline.getRadius() <= 0f) return null;
        Rect rect = new Rect();
        if (outline.getRect(rect) && rect.width() > 0 && rect.height() > 0) {
            return new Shape(new RectF(rect), outline.getRadius());
        }
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        return new Shape(
                new RectF(0f, 0f, view.getWidth(), view.getHeight()),
                outline.getRadius());
    }

}
