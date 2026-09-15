package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the floating keyboard from stable widget types, parent/child topology and runtime
 * geometry. No R8 class/member names or aapt2 resource IDs are part of this contract.
 */
final class GboardFloatingStructureResolver {
    static final String KEYBOARD_HOLDER_CLASS =
            "com.google.android.libraries.inputmethod.widgets.KeyboardHolder";
    private static final String KEYBOARD_VIEW_HOLDER_CLASS =
            "com.google.android.libraries.inputmethod.keyboard.impl.KeyboardViewHolder";

    static final class Structure {
        final View popupContent;
        final ViewGroup keyboardArea;
        final View stockBackground;
        final ViewGroup contentColumn;
        final ViewGroup keyboardHolder;
        final View bottomFrame;
        final View topEdge;
        final List<ViewGroup> keyboardViewHolders;

        Structure(
                View popupContent,
                ViewGroup keyboardArea,
                View stockBackground,
                ViewGroup contentColumn,
                ViewGroup keyboardHolder,
                View bottomFrame,
                View topEdge,
                List<ViewGroup> keyboardViewHolders) {
            this.popupContent = popupContent;
            this.keyboardArea = keyboardArea;
            this.stockBackground = stockBackground;
            this.contentColumn = contentColumn;
            this.keyboardHolder = keyboardHolder;
            this.bottomFrame = bottomFrame;
            this.topEdge = topEdge;
            this.keyboardViewHolders = Collections.unmodifiableList(
                    new ArrayList<>(keyboardViewHolders));
        }
    }

    private GboardFloatingStructureResolver() {}

    static Structure resolve(View popupContent, ClassLoader classLoader) {
        if (popupContent == null || classLoader == null) return null;
        try {
            Class<?> keyboardHolderClass = Class.forName(
                    KEYBOARD_HOLDER_CLASS, false, classLoader);
            View holderCandidate = findUniqueDescendant(popupContent, keyboardHolderClass);
            if (!(holderCandidate instanceof ViewGroup)) return null;
            return resolveFromKeyboardHolder((ViewGroup) holderCandidate, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Structure resolveFromKeyboardHolder(ViewGroup keyboardHolder, ClassLoader classLoader) {
        if (keyboardHolder == null || classLoader == null) return null;
        try {
            Class<?> keyboardViewHolderClass = Class.forName(
                    KEYBOARD_VIEW_HOLDER_CLASS, false, classLoader);

            if (!(keyboardHolder.getParent() instanceof ViewGroup)) return null;
            ViewGroup contentColumn = (ViewGroup) keyboardHolder.getParent();
            if (!(contentColumn.getParent() instanceof ViewGroup)) return null;
            ViewGroup keyboardArea = (ViewGroup) contentColumn.getParent();

            int contentIndex = keyboardArea.indexOfChild(contentColumn);
            int holderIndex = contentColumn.indexOfChild(keyboardHolder);
            if (contentIndex <= 0 || holderIndex < 0) return null;

            View stockBackground = keyboardArea.getChildAt(contentIndex - 1);
            if (stockBackground == null || stockBackground == contentColumn) return null;

            View bottomFrame = findBottomSibling(contentColumn, keyboardHolder, holderIndex);
            if (bottomFrame == null) return null;

            ArrayList<ViewGroup> holders = new ArrayList<>();
            View topEdge = null;
            int firstKeyboardViewHolderIndex = -1;
            for (int i = 0; i < keyboardHolder.getChildCount(); i++) {
                View child = keyboardHolder.getChildAt(i);
                if (child == null) continue;
                if (keyboardViewHolderClass.isInstance(child) && child instanceof ViewGroup) {
                    if (firstKeyboardViewHolderIndex < 0) firstKeyboardViewHolderIndex = i;
                    holders.add((ViewGroup) child);
                }
            }
            if (holders.size() < 2 || firstKeyboardViewHolderIndex <= 0) return null;
            for (int i = firstKeyboardViewHolderIndex - 1; i >= 0; i--) {
                View candidate = keyboardHolder.getChildAt(i);
                if (candidate != null && !keyboardViewHolderClass.isInstance(candidate)) {
                    topEdge = candidate;
                    break;
                }
            }
            if (topEdge == null) return null;

            return new Structure(
                    keyboardArea,
                    keyboardArea,
                    stockBackground,
                    contentColumn,
                    keyboardHolder,
                    bottomFrame,
                    topEdge,
                    holders);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean isFloatingGeometry(Structure structure) {
        if (structure == null || structure.keyboardArea == null) return false;
        ViewGroup keyboardArea = structure.keyboardArea;
        int width = keyboardArea.getWidth();
        int height = keyboardArea.getHeight();
        if (width <= 0 || height <= 0) return false;

        View root = keyboardArea.getRootView();
        if (root == null) return false;
        int[] areaLocation = new int[2];
        int[] rootLocation = new int[2];
        keyboardArea.getLocationInWindow(areaLocation);
        root.getLocationInWindow(rootLocation);

        DisplayMetrics metrics = keyboardArea.getResources().getDisplayMetrics();
        int displayWidth = metrics != null ? metrics.widthPixels : 0;
        if (displayWidth <= 0) displayWidth = Math.max(root.getWidth(), width);
        float density = metrics != null && metrics.density > 0f ? metrics.density : 1f;
        int minimumInset = Math.max(Math.round(24f * density), displayWidth / 12);

        boolean compactWidth = width <= displayWidth - (minimumInset * 2);
        boolean offsetWithinRoot = areaLocation[0] - rootLocation[0] >= minimumInset
                || (root.getWidth() > 0
                && root.getWidth() - (areaLocation[0] - rootLocation[0]) - width >= minimumInset);
        boolean raisedOrRounded = keyboardArea.getElevation() > 0f
                || hasRoundedOutline(keyboardArea)
                || hasRoundedOutline(structure.stockBackground);

        return compactWidth && raisedOrRounded && (offsetWithinRoot || root.getWidth() <= width + minimumInset);
    }

    private static boolean hasRoundedOutline(View view) {
        if (view == null) return false;
        try {
            Outline outline = new Outline();
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider != null) {
                provider.getOutline(view, outline);
                if (outline.getRadius() > 0f) return true;
            }
        } catch (Throwable ignored) {}
        try {
            Drawable background = view.getBackground();
            if (background != null) {
                Outline outline = new Outline();
                background.getOutline(outline);
                return outline.getRadius() > 0f;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static View findUniqueDescendant(View root, Class<?> targetClass) {
        View[] match = new View[1];
        if (!collectUnique(root, targetClass, match)) return null;
        return match[0];
    }

    private static boolean collectUnique(View view, Class<?> targetClass, View[] match) {
        if (view == null) return true;
        if (targetClass.isInstance(view)) {
            if (match[0] != null && match[0] != view) return false;
            match[0] = view;
        }
        if (!(view instanceof ViewGroup)) return true;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (!collectUnique(group.getChildAt(i), targetClass, match)) return false;
        }
        return true;
    }

    private static View findBottomSibling(
            ViewGroup contentColumn, ViewGroup keyboardHolder, int holderIndex) {
        for (int i = holderIndex + 1; i < contentColumn.getChildCount(); i++) {
            View candidate = contentColumn.getChildAt(i);
            if (candidate instanceof ViewGroup && candidate != keyboardHolder) return candidate;
        }
        return null;
    }
}
