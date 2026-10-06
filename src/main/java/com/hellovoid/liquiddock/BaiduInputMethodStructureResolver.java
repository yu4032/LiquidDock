package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

/**
 * Resolves the shell passed through Baidu's semantic floating-keyboard lifecycle.
 *
 * <p>The production contract deliberately avoids R8 names and fixed resource IDs. The semantic
 * entry point is ImeService.showFloatKeyboardView(View); from that authoritative View we choose the
 * largest compact ancestor that still has a parent, then locate the stock background drawable on
 * the shell, a shell-sized child, or a shell-sized sibling.</p>
 */
final class BaiduInputMethodStructureResolver {
    private static final String KEYBOARD_REGION_CLASS =
            "com.content.simeji.inputview.KeyboardRegion";
    private static final String KEYBOARD_CONTAINER_CLASS =
            "com.content.simeji.inputview.KeyboardContainer";
    private static final String INPUT_VIEW_CLASS =
            "com.content.simeji.inputview.InputView";

    static final class Structure {
        final View authoritativeFloatView;
        final ViewGroup sinkHost;
        final View glassTarget;
        final View stockBackgroundOwner;
        final Drawable stockBackgroundDrawable;

        Structure(
                View authoritativeFloatView,
                ViewGroup sinkHost,
                View glassTarget,
                View stockBackgroundOwner,
                Drawable stockBackgroundDrawable) {
            this.authoritativeFloatView = authoritativeFloatView;
            this.sinkHost = sinkHost;
            this.glassTarget = glassTarget;
            this.stockBackgroundOwner = stockBackgroundOwner;
            this.stockBackgroundDrawable = stockBackgroundDrawable;
        }
    }

    private BaiduInputMethodStructureResolver() {}

    static Structure resolveFromFloatView(View authoritativeFloatView) {
        if (authoritativeFloatView == null) return null;
        View shell = chooseFloatingShell(authoritativeFloatView);
        if (shell == null || !(shell.getParent() instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) shell.getParent();

        View backgroundOwner = findStockBackgroundOwner(shell, host);
        Drawable stockBackground = backgroundOwner != null ? backgroundOwner.getBackground() : null;
        if (stockBackground == null) return null;

        return new Structure(
                authoritativeFloatView,
                host,
                shell,
                backgroundOwner,
                stockBackground);
    }

    /**
     * Stable structural fallback used only when Baidu's semantic floating callback was not seen.
     *
     * <p>This intentionally relies only on decompiled stable keyboard view classes plus runtime
     * geometry. Obfuscated implementation names and resource IDs remain excluded.</p>
     */
    static Structure resolveFromInputView(View authoritativeInputView, ClassLoader classLoader) {
        if (authoritativeInputView == null || classLoader == null) return null;
        try {
            Class<?> keyboardRegionClass =
                    Class.forName(KEYBOARD_REGION_CLASS, false, classLoader);
            Class<?> keyboardContainerClass =
                    Class.forName(KEYBOARD_CONTAINER_CLASS, false, classLoader);
            Class<?> inputViewClass =
                    Class.forName(INPUT_VIEW_CLASS, false, classLoader);

            View content = chooseStableKeyboardContent(
                    findDescendant(authoritativeInputView, keyboardRegionClass),
                    findDescendant(authoritativeInputView, keyboardContainerClass),
                    findDescendant(authoritativeInputView, inputViewClass));
            if (content == null || !(content.getParent() instanceof ViewGroup)) return null;

            ViewGroup host = (ViewGroup) content.getParent();
            View backgroundOwner = findStockBackgroundOwner(content, host);
            Drawable stockBackground =
                    backgroundOwner != null ? backgroundOwner.getBackground() : null;
            if (stockBackground == null || !roughlySameBounds(backgroundOwner, content)) return null;

            return new Structure(
                    authoritativeInputView,
                    host,
                    content,
                    backgroundOwner,
                    stockBackground);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean isFloatingGeometry(Structure structure) {
        return structure != null && isCompactFloatingCandidate(structure.glassTarget);
    }

    static float resolveCornerRadiusPx(Structure structure) {
        if (structure == null) return 0f;
        float radius = drawableOutlineRadius(structure.stockBackgroundDrawable);
        if (radius > 0f) return radius;
        radius = outlineRadius(structure.stockBackgroundOwner);
        if (radius > 0f) return radius;
        return outlineRadius(structure.glassTarget);
    }

    static String describe(View seed) {
        if (seed == null) return "view=null";
        StringBuilder out = new StringBuilder();
        View current = seed;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (depth > 0) out.append(" <- ");
            out.append(shortName(current))
                    .append('[').append(current.getWidth()).append('x').append(current.getHeight())
                    .append(" bg=").append(backgroundName(current))
                    .append(" alpha=").append(current.getAlpha())
                    .append(" attached=").append(current.isAttachedToWindow())
                    .append(']');
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return out.toString();
    }

    static String describe(Structure structure) {
        if (structure == null) return "structure=null";
        return "target=" + shortName(structure.glassTarget)
                + "[" + structure.glassTarget.getWidth() + "x" + structure.glassTarget.getHeight() + "]"
                + " host=" + shortName(structure.sinkHost)
                + " stockOwner=" + shortName(structure.stockBackgroundOwner)
                + " stock=" + structure.stockBackgroundDrawable.getClass().getName();
    }

    private static View chooseStableKeyboardContent(
            View region, View container, View inputView) {
        if (region != null && region.getParent() instanceof ViewGroup) return region;
        if (container != null && container.getParent() instanceof ViewGroup) return container;
        if (inputView != null && inputView.getParent() instanceof ViewGroup) return inputView;
        return null;
    }

    private static View findDescendant(View root, Class<?> type) {
        if (root == null || type == null) return null;
        if (type.isInstance(root)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findDescendant(group.getChildAt(i), type);
            if (match != null) return match;
        }
        return null;
    }

    private static View chooseFloatingShell(View seed) {
        View root = seed.getRootView();
        View best = null;
        long bestArea = -1L;
        View current = seed;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current != root && current.getParent() instanceof ViewGroup
                    && isCompactFloatingCandidate(current)) {
                long area = (long) current.getWidth() * (long) current.getHeight();
                if (area > bestArea) {
                    best = current;
                    bestArea = area;
                }
            }
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return best;
    }

    private static boolean isCompactFloatingCandidate(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        int displayWidth = metrics != null && metrics.widthPixels > 0
                ? metrics.widthPixels : view.getRootView().getWidth();
        int displayHeight = metrics != null && metrics.heightPixels > 0
                ? metrics.heightPixels : view.getRootView().getHeight();
        if (displayWidth <= 0 || displayHeight <= 0) return false;
        float density = metrics != null && metrics.density > 0f ? metrics.density : 1f;

        int minWidth = Math.max(Math.round(180f * density), displayWidth / 5);
        int minHeight = Math.max(Math.round(120f * density), displayHeight / 8);
        int horizontalInset = Math.max(Math.round(20f * density), displayWidth / 18);
        int width = view.getWidth();
        int height = view.getHeight();
        if (width < minWidth || height < minHeight
                || width > displayWidth - horizontalInset * 2) return false;

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int leftInset = location[0];
        int rightInset = displayWidth - location[0] - width;
        return leftInset >= horizontalInset || rightInset >= horizontalInset;
    }

    private static View findStockBackgroundOwner(View shell, ViewGroup host) {
        if (shell.getBackground() != null) return shell;

        if (shell instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) shell;
            View best = null;
            long bestArea = -1L;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == null || child.getBackground() == null) continue;
                if (!coversMostOf(child, shell)) continue;
                long area = (long) child.getWidth() * (long) child.getHeight();
                if (area > bestArea) {
                    best = child;
                    bestArea = area;
                }
            }
            if (best != null) return best;
        }

        int shellIndex = host.indexOfChild(shell);
        for (int i = shellIndex - 1; i >= 0; i--) {
            View sibling = host.getChildAt(i);
            if (sibling == null || sibling.getBackground() == null) continue;
            if (roughlySameBounds(sibling, shell)) return sibling;
        }

        if (host.getBackground() != null && roughlySameBounds(host, shell)) return host;
        return null;
    }

    private static boolean coversMostOf(View candidate, View target) {
        if (candidate == null || target == null
                || candidate.getWidth() <= 0 || candidate.getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0) return false;
        if (candidate.getWidth() < target.getWidth() * 0.85f
                || candidate.getHeight() < target.getHeight() * 0.85f) return false;
        int[] a = new int[2];
        int[] b = new int[2];
        candidate.getLocationInWindow(a);
        target.getLocationInWindow(b);
        float density = target.getResources().getDisplayMetrics().density;
        int tolerance = Math.max(8, Math.round(12f * Math.max(1f, density)));
        return Math.abs(a[0] - b[0]) <= tolerance
                && Math.abs(a[1] - b[1]) <= tolerance;
    }

    private static boolean roughlySameBounds(View a, View b) {
        if (a == null || b == null || a.getWidth() <= 0 || a.getHeight() <= 0
                || b.getWidth() <= 0 || b.getHeight() <= 0) return false;
        int[] aLocation = new int[2];
        int[] bLocation = new int[2];
        a.getLocationInWindow(aLocation);
        b.getLocationInWindow(bLocation);
        int tolerance = Math.max(8,
                Math.round(12f * Math.max(1f, b.getResources().getDisplayMetrics().density)));
        return Math.abs(aLocation[0] - bLocation[0]) <= tolerance
                && Math.abs(aLocation[1] - bLocation[1]) <= tolerance
                && Math.abs(a.getWidth() - b.getWidth()) <= tolerance
                && Math.abs(a.getHeight() - b.getHeight()) <= tolerance;
    }

    private static float outlineRadius(View view) {
        if (view == null) return 0f;
        try {
            Outline outline = new Outline();
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider != null) {
                provider.getOutline(view, outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
        } catch (Throwable ignored) {}
        return drawableOutlineRadius(view.getBackground());
    }

    private static float drawableOutlineRadius(Drawable drawable) {
        if (drawable == null) return 0f;
        try {
            Outline outline = new Outline();
            drawable.getOutline(outline);
            return Math.max(0f, outline.getRadius());
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static String shortName(View view) {
        if (view == null) return "null";
        String name = view.getClass().getSimpleName();
        return name == null || name.isEmpty() ? view.getClass().getName() : name;
    }

    private static String backgroundName(View view) {
        Drawable background = view != null ? view.getBackground() : null;
        return background == null ? "null" : background.getClass().getSimpleName();
    }
}
