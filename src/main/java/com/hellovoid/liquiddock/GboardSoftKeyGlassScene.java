package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import com.hellovoid.prismal.PrismalInteractionState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resolves only actual keyboard SoftKeyView keycaps into Prismal nodes.
 *
 * <p>Some Gboard keys use an oversized SoftKeyView as a layout/touch container and put the
 * visible keycap background on a smaller descendant. This resolver separates the logical key from
 * the actual background owner so glass follows the visual keycap rather than the touch cell.</p>
 */
final class GboardSoftKeyGlassScene {
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String SOFT_KEYBOARD_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView";
    private static final float FALLBACK_INSET_X_FRACTION = 0.06f;
    private static final float FALLBACK_INSET_Y_FRACTION = 0.08f;
    private static final float SPECIAL_KEY_FALLBACK_RADIUS_DP = 8f;
    private static final PrismalInteractionState PRESSED =
            new PrismalInteractionState(1f, 0.5f, 0.5f);
    static final Node[] EMPTY = new Node[0];

    private static final WeakHashMap<View, Shape> SHAPE_BY_BACKGROUND_VIEW =
            new WeakHashMap<>();
    private static final WeakHashMap<View, View> PREPARED_BACKGROUND_BY_KEY =
            new WeakHashMap<>();

    private static final class Shape {
        final RectF bounds;
        final float radius;

        Shape(RectF bounds, float radius) {
            this.bounds = new RectF(bounds);
            this.radius = Math.max(0f, radius);
        }

        float area() {
            return Math.max(0f, bounds.width()) * Math.max(0f, bounds.height());
        }
    }

    private static final class VisualTarget {
        final View key;
        final View backgroundView;
        final Shape shape;

        VisualTarget(View key, View backgroundView, Shape shape) {
            this.key = key;
            this.backgroundView = backgroundView;
            this.shape = shape;
        }
    }

    private static final class ShapeTemplate {
        final float widthPx;
        final float heightPx;

        ShapeTemplate(Shape shape) {
            widthPx = Math.max(1f, shape.bounds.width());
            heightPx = Math.max(1f, shape.bounds.height());
        }

        RectF centeredIn(View target) {
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            float width = Math.min(widthPx, target.getWidth());
            float height = Math.min(heightPx, target.getHeight());
            float left = (target.getWidth() - width) * 0.5f;
            float top = (target.getHeight() - height) * 0.5f;
            return new RectF(left, top, left + width, top + height);
        }
    }

    static final class Node {
        final View keyView;
        final View backgroundView;
        final GboardFloatingGlassGeometry geometry;
        final PrismalInteractionState interaction;
        final boolean pressed;

        Node(
                View keyView,
                View backgroundView,
                GboardFloatingGlassGeometry geometry,
                PrismalInteractionState interaction,
                boolean pressed) {
            this.keyView = keyView;
            this.backgroundView = backgroundView;
            this.geometry = geometry;
            this.interaction = interaction;
            this.pressed = pressed;
        }

        Node withGeometry(GboardFloatingGlassGeometry nextGeometry) {
            return new Node(keyView, backgroundView, nextGeometry, interaction, pressed);
        }

        Node withPressed(boolean nextPressed) {
            return new Node(
                    keyView,
                    backgroundView,
                    geometry,
                    nextPressed ? PRESSED : PrismalInteractionState.IDLE,
                    nextPressed);
        }
    }

    private GboardSoftKeyGlassScene() {}

    static Node[] capture(
            GboardFloatingGlassGeometry.CaptureContext context,
            GboardFloatingStructureResolver.Structure structure,
            boolean enabled,
            float customCornerRadiusDp) {
        if (context == null || structure == null) return EMPTY;

        ArrayList<View> keys = new ArrayList<>();
        for (ViewGroup holder : structure.keyboardViewHolders) {
            collectKeyboardSoftKeys(holder, keys);
        }
        if (keys.isEmpty()) return EMPTY;

        if (!enabled) {
            synchronized (GboardSoftKeyGlassScene.class) {
                for (View key : keys) PREPARED_BACKGROUND_BY_KEY.remove(key);
            }
            return EMPTY;
        }

        Map<View, VisualTarget> targets = new IdentityHashMap<>();
        ShapeTemplate standardTemplate = null;
        for (View key : keys) {
            if (!isDrawableTarget(key)) continue;
            VisualTarget target = resolveVisualTarget(key);
            if (target == null) continue;
            targets.put(key, target);
            if (standardTemplate == null
                    && target.backgroundView == key
                    && !isCustomRadiusExempt(key)
                    && target.shape != null) {
                standardTemplate = new ShapeTemplate(target.shape);
            }
        }

        ArrayList<Node> nodes = new ArrayList<>(keys.size());
        for (View key : keys) {
            synchronized (GboardSoftKeyGlassScene.class) {
                PREPARED_BACKGROUND_BY_KEY.remove(key);
            }
            if (!isDrawableTarget(key)) continue;

            VisualTarget target = targets.get(key);
            View geometryView = key;
            RectF bounds = null;
            float nativeRadius = 0f;

            if (target != null && target.shape != null) {
                geometryView = target.backgroundView;
                bounds = target.shape.bounds;
                nativeRadius = target.shape.radius;
                if (target.backgroundView == key
                        && standardTemplate != null
                        && !keepsNativeWideGeometry(key)
                        && (bounds.width() > standardTemplate.widthPx * 1.30f
                        || bounds.height() > standardTemplate.heightPx * 1.30f)) {
                    bounds = standardTemplate.centeredIn(key);
                }
            } else if (standardTemplate != null) {
                bounds = standardTemplate.centeredIn(key);
            }
            if (bounds == null) bounds = fallbackBounds(key);
            if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) continue;

            boolean exempt = isCustomRadiusExempt(key);
            float radiusPx = exempt
                    ? (nativeRadius > 0f ? nativeRadius : dp(key, SPECIAL_KEY_FALLBACK_RADIUS_DP))
                    : dp(key, Math.max(0f, Math.min(24f, customCornerRadiusDp)));

            GboardFloatingGlassGeometry geometry =
                    GboardFloatingGlassGeometry.captureTargetRect(
                            context, geometryView, bounds, radiusPx);
            if (geometry == null) continue;

            View backgroundView = target != null ? target.backgroundView : key;
            boolean pressed = key.isPressed();
            nodes.add(new Node(
                    key,
                    backgroundView,
                    geometry,
                    pressed ? PRESSED : PrismalInteractionState.IDLE,
                    pressed));
            synchronized (GboardSoftKeyGlassScene.class) {
                PREPARED_BACKGROUND_BY_KEY.put(key, backgroundView);
            }
        }
        return nodes.isEmpty() ? EMPTY : nodes.toArray(new Node[0]);
    }

    static Node[] translateAndRefreshInteraction(
            Node[] source,
            float dx,
            float dy) {
        Node[] input = source != null ? source : EMPTY;
        if (input.length == 0) return EMPTY;
        Node[] out = null;
        for (int i = 0; i < input.length; i++) {
            Node node = input[i];
            if (node == null || node.geometry == null) continue;
            boolean pressed = node.keyView != null && node.keyView.isPressed();
            GboardFloatingGlassGeometry geometry = node.geometry.translated(dx, dy);
            if (pressed != node.pressed || geometry != node.geometry) {
                if (out == null) out = input.clone();
                Node next = node;
                if (geometry != node.geometry) next = next.withGeometry(geometry);
                if (pressed != next.pressed) next = next.withPressed(pressed);
                out[i] = next;
            }
        }
        return out != null ? out : input;
    }

    static synchronized View preparedBackgroundTarget(View key) {
        return key != null ? PREPARED_BACKGROUND_BY_KEY.get(key) : null;
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

    static boolean isSoftKeyboardView(View view) {
        if (view == null) return false;
        Class<?> type = view.getClass();
        while (type != null) {
            if (SOFT_KEYBOARD_VIEW_CLASS.equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    static synchronized void rememberBackground(View view, Drawable background) {
        if (view == null || background == null) return;
        Shape shape = backgroundShape(background, view);
        if (shape != null) SHAPE_BY_BACKGROUND_VIEW.put(view, shape);
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
                    || left.keyView != right.keyView
                    || left.backgroundView != right.backgroundView
                    || left.pressed != right.pressed
                    || left.geometry == null
                    || !left.geometry.sameAs(right.geometry)) {
                return false;
            }
        }
        return true;
    }

    private static boolean keepsNativeWideGeometry(View view) {
        String name = resourceEntryName(view);
        if (name == null) return false;
        return name.contains("shift")
                || name.contains("del")
                || name.contains("delete")
                || name.contains("space")
                || name.contains("enter")
                || name.contains("ime_action")
                || name.contains("switch_to_symbol")
                || name.contains("switch_to_non_prime")
                || name.contains("language")
                || name.contains("emoji");
    }

    static boolean isCustomRadiusExempt(View view) {
        String name = resourceEntryName(view);
        if (name == null) return false;
        return name.contains("switch_to_symbol")
                || name.contains("switch_to_non_prime")
                || name.contains("ime_action")
                || name.equals("key_pos_enter");
    }

    private static VisualTarget resolveVisualTarget(View key) {
        if (key == null) return null;

        VisualTarget descendant = findBestDescendantBackground(key);
        Shape directShape = resolveNativeShape(key);
        if (descendant != null && descendant.shape != null) {
            float keyArea = Math.max(1f, key.getWidth() * (float) key.getHeight());
            float descendantArea = descendant.shape.area();
            boolean meaningfullySmaller = descendantArea < keyArea * 0.90f;
            boolean semanticBackground = tagContainsBackground(descendant.backgroundView);
            if (meaningfullySmaller || semanticBackground || directShape == null) {
                return descendant;
            }
        }
        if (directShape != null) return new VisualTarget(key, key, directShape);
        return descendant;
    }

    private static VisualTarget findBestDescendantBackground(View key) {
        if (!(key instanceof ViewGroup)) return null;
        ViewGroup root = (ViewGroup) key;
        VisualTarget best = null;
        long bestScore = Long.MIN_VALUE;
        ArrayList<View> stack = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != null) stack.add(child);
        }
        while (!stack.isEmpty()) {
            View candidate = stack.remove(stack.size() - 1);
            if (candidate instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) candidate;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child != null) stack.add(child);
                }
            }
            if (!isDrawableTarget(candidate)
                    || candidate instanceof TextView
                    || candidate instanceof ImageView) {
                continue;
            }
            Shape shape = resolveNativeShape(candidate);
            if (shape == null) continue;

            float area = shape.area();
            if (area <= 0f) continue;
            long score = Math.round(area);
            if (tagContainsBackground(candidate)) score += 1_000_000_000L;
            float keyArea = Math.max(1f, key.getWidth() * (float) key.getHeight());
            if (area < keyArea * 0.90f) score += 500_000_000L;
            if (area < keyArea * 0.35f) score -= 250_000_000L;
            if (score > bestScore) {
                bestScore = score;
                best = new VisualTarget(key, candidate, shape);
            }
        }
        return best;
    }

    private static boolean tagContainsBackground(View view) {
        if (view == null) return false;
        try {
            Object tag = view.getTag();
            return tag != null && String.valueOf(tag).contains("background");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void collectKeyboardSoftKeys(View view, List<View> out) {
        if (view == null || out == null) return;
        if (isSoftKeyboardView(view)) {
            collectSoftKeysInsideKeyboard(view, out);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            collectKeyboardSoftKeys(group.getChildAt(i), out);
        }
    }

    private static void collectSoftKeysInsideKeyboard(View view, List<View> out) {
        if (view == null || out == null) return;
        if (isSoftKeyView(view)) {
            out.add(view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            collectSoftKeysInsideKeyboard(group.getChildAt(i), out);
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
        synchronized (GboardSoftKeyGlassScene.class) {
            Shape cached = SHAPE_BY_BACKGROUND_VIEW.get(view);
            if (cached != null) return cached;
        }
        Shape shape = backgroundShape(view.getBackground(), view);
        if (shape == null) shape = outlineShape(view);
        synchronized (GboardSoftKeyGlassScene.class) {
            if (shape != null) SHAPE_BY_BACKGROUND_VIEW.put(view, shape);
        }
        return shape;
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

    private static Shape backgroundShape(Drawable drawable, View view) {
        if (drawable == null || view == null
                || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        try {
            Rect padding = new Rect();
            drawable.getPadding(padding);
            float left = Math.max(0, padding.left);
            float top = Math.max(0, padding.top);
            float right = Math.min(view.getWidth(), view.getWidth() - Math.max(0, padding.right));
            float bottom = Math.min(view.getHeight(), view.getHeight() - Math.max(0, padding.bottom));
            if (right > left && bottom > top
                    && (padding.left > 0 || padding.top > 0
                    || padding.right > 0 || padding.bottom > 0)) {
                Outline outline = new Outline();
                drawable.getOutline(outline);
                return new Shape(
                        new RectF(left, top, right, bottom),
                        Math.max(0f, outline.getRadius()));
            }
        } catch (Throwable ignored) {}
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
}
