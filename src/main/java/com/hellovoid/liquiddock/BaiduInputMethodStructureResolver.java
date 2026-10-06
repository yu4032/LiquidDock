package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

/**
 * Resolves supported Baidu Input Method keyboard layouts from stable semantic view types,
 * parent/child topology and runtime geometry. Obfuscated implementation names and resource IDs
 * are deliberately excluded from this contract.
 */
final class BaiduInputMethodStructureResolver {
    static final String IME_SERVICE_CLASS = "com.content.input_mi.ImeService";
    private static final String KEYBOARD_REGION_CLASS =
            "com.content.simeji.inputview.KeyboardRegion";
    private static final String KEYBOARD_CONTAINER_CLASS =
            "com.content.simeji.inputview.KeyboardContainer";
    private static final String INPUT_VIEW_CLASS =
            "com.content.simeji.inputview.InputView";

    static final class Structure {
        final View authoritativeInputView;
        final ViewGroup sinkHost;
        final View contentView;
        final View backgroundFrame;

        Structure(
                View authoritativeInputView,
                ViewGroup sinkHost,
                View contentView,
                View backgroundFrame) {
            this.authoritativeInputView = authoritativeInputView;
            this.sinkHost = sinkHost;
            this.contentView = contentView;
            this.backgroundFrame = backgroundFrame;
        }
    }

    private BaiduInputMethodStructureResolver() {}

    static Structure resolve(View authoritativeInputView, ClassLoader classLoader) {
        if (authoritativeInputView == null || classLoader == null) return null;
        try {
            Class<?> keyboardRegionClass = Class.forName(
                    KEYBOARD_REGION_CLASS, false, classLoader);
            Class<?> keyboardContainerClass = Class.forName(
                    KEYBOARD_CONTAINER_CLASS, false, classLoader);
            Class<?> inputViewClass = Class.forName(INPUT_VIEW_CLASS, false, classLoader);

            View region = findDescendant(authoritativeInputView, keyboardRegionClass);
            View container = findDescendant(authoritativeInputView, keyboardContainerClass);
            View inputView = findDescendant(authoritativeInputView, inputViewClass);
            View content = chooseContent(region, container, inputView);
            if (content == null || !(content.getParent() instanceof ViewGroup)) return null;

            ViewGroup host = (ViewGroup) content.getParent();
            int contentIndex = host.indexOfChild(content);
            if (contentIndex <= 0) return null;

            View background = findDistinctBackgroundSibling(host, content, contentIndex);
            if (background == null) return null;
            if (!roughlySameBounds(background, content)) return null;

            return new Structure(authoritativeInputView, host, content, background);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean isFloatingGeometry(Structure structure) {
        if (structure == null || structure.contentView == null) return false;
        View content = structure.contentView;
        int width = content.getWidth();
        int height = content.getHeight();
        if (width <= 0 || height <= 0) return false;

        View root = content.getRootView();
        if (root == null || root.getWidth() <= 0) return false;
        int[] contentLocation = new int[2];
        int[] rootLocation = new int[2];
        content.getLocationInWindow(contentLocation);
        root.getLocationInWindow(rootLocation);

        DisplayMetrics metrics = content.getResources().getDisplayMetrics();
        int displayWidth = metrics != null && metrics.widthPixels > 0
                ? metrics.widthPixels : root.getWidth();
        float density = metrics != null && metrics.density > 0f ? metrics.density : 1f;
        int minimumInset = Math.max(Math.round(20f * density), displayWidth / 14);
        int leftInset = contentLocation[0] - rootLocation[0];
        int rightInset = root.getWidth() - leftInset - width;

        boolean compactWidth = width <= displayWidth - minimumInset * 2;
        boolean horizontallyInset = leftInset >= minimumInset || rightInset >= minimumInset;
        boolean shaped = content.getElevation() > 0f
                || hasRoundedOutline(structure.backgroundFrame)
                || hasRoundedOutline(content);
        return compactWidth && horizontallyInset && shaped;
    }

    static float resolveCornerRadiusPx(Structure structure) {
        if (structure == null) return 0f;
        float radius = outlineRadius(structure.backgroundFrame);
        if (radius > 0f) return radius;
        return outlineRadius(structure.contentView);
    }

    private static View chooseContent(View region, View container, View inputView) {
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
            View child = group.getChildAt(i);
            View match = findDescendant(child, type);
            if (match != null) return match;
        }
        return null;
    }

    private static View findDistinctBackgroundSibling(
            ViewGroup host, View content, int contentIndex) {
        for (int i = contentIndex - 1; i >= 0; i--) {
            View candidate = host.getChildAt(i);
            if (candidate == null || candidate == content) continue;
            if (candidate.getBackground() == null) continue;
            return candidate;
        }
        return null;
    }

    private static boolean roughlySameBounds(View a, View b) {
        if (a == null || b == null || a.getWidth() <= 0 || a.getHeight() <= 0
                || b.getWidth() <= 0 || b.getHeight() <= 0) return false;
        int[] aLocation = new int[2];
        int[] bLocation = new int[2];
        a.getLocationInWindow(aLocation);
        b.getLocationInWindow(bLocation);
        int tolerance = Math.max(8,
                Math.round(8f * b.getResources().getDisplayMetrics().density));
        return Math.abs(aLocation[0] - bLocation[0]) <= tolerance
                && Math.abs(aLocation[1] - bLocation[1]) <= tolerance
                && Math.abs(a.getWidth() - b.getWidth()) <= tolerance
                && Math.abs(a.getHeight() - b.getHeight()) <= tolerance;
    }

    private static boolean hasRoundedOutline(View view) {
        return outlineRadius(view) > 0f;
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
        try {
            Drawable background = view.getBackground();
            if (background != null) {
                Outline outline = new Outline();
                background.getOutline(outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
        } catch (Throwable ignored) {}
        return 0f;
    }
}
