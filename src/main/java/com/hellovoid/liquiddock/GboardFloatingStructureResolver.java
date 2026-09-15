package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the floating keyboard from stable widget types and parent/child topology.
 * No R8 class/member names or aapt2 resource IDs are part of this contract.
 */
final class GboardFloatingStructureResolver {
    private static final String KEYBOARD_HOLDER_CLASS =
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
            Class<?> keyboardViewHolderClass = Class.forName(
                    KEYBOARD_VIEW_HOLDER_CLASS, false, classLoader);
            View holderCandidate = findUniqueDescendant(popupContent, keyboardHolderClass);
            if (!(holderCandidate instanceof ViewGroup)) return null;
            ViewGroup keyboardHolder = (ViewGroup) holderCandidate;

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
            // Both audited layouts contain aux/header/main holders. Require at least header+main
            // so ordinary Gboard popups cannot be mistaken for the floating keyboard.
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
                    popupContent,
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
        // The floating layout keeps the utility/navigation bottom frame after KeyboardHolder.
        // Prefer the first ViewGroup after it; do not depend on its compiled ID or class name.
        for (int i = holderIndex + 1; i < contentColumn.getChildCount(); i++) {
            View candidate = contentColumn.getChildAt(i);
            if (candidate instanceof ViewGroup && candidate != keyboardHolder) return candidate;
        }
        return null;
    }
}
