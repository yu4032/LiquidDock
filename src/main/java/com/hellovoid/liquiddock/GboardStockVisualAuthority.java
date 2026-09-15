package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns only the structurally resolved floating-keyboard container fills while glass is presented.
 * A pre-draw barrier reasserts transparency after Gboard theme/layout updates without depending on
 * any R8-obfuscated manager class, method or field name.
 */
final class GboardStockVisualAuthority {
    private static final Object LOCK = new Object();
    private static final String SOFT_KEYBOARD_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView";

    private static final class Claim {
        final GboardFloatingStructureResolver.Structure structure;
        final Map<View, Drawable> backgrounds = new WeakHashMap<>();
        final float baseElevation;
        final float stockAlpha;
        final ViewTreeObserver.OnPreDrawListener preDrawListener;

        Claim(GboardFloatingStructureResolver.Structure structure) {
            this.structure = structure;
            this.baseElevation = structure.keyboardArea.getElevation();
            this.stockAlpha = structure.stockBackground.getAlpha();
            rememberBackground(structure.keyboardArea);
            rememberBackground(structure.contentColumn);
            rememberBackground(structure.keyboardHolder);
            rememberBackground(structure.bottomFrame);
            rememberBackground(structure.topEdge);
            for (View holder : structure.keyboardViewHolders) rememberBackground(holder);
            preDrawListener = () -> {
                applyClaim(this);
                return true;
            };
        }

        void rememberBackground(View view) {
            if (view != null && !backgrounds.containsKey(view)) {
                backgrounds.put(view, view.getBackground());
            }
        }
    }

    private static final Map<View, Claim> BY_BASE = new WeakHashMap<>();

    private GboardStockVisualAuthority() {}

    static boolean claim(GboardFloatingStructureResolver.Structure structure) {
        if (structure == null || structure.keyboardArea == null
                || structure.stockBackground == null || structure.bottomFrame == null) return false;
        Claim claim;
        synchronized (LOCK) {
            Claim existing = BY_BASE.get(structure.keyboardArea);
            if (existing != null) {
                applyClaim(existing);
                return true;
            }
            claim = new Claim(structure);
            BY_BASE.put(structure.keyboardArea, claim);
        }
        ViewTreeObserver observer = structure.keyboardArea.getViewTreeObserver();
        if (!observer.isAlive()) {
            synchronized (LOCK) { BY_BASE.remove(structure.keyboardArea); }
            return false;
        }
        observer.addOnPreDrawListener(claim.preDrawListener);
        applyClaim(claim);
        return true;
    }

    static void release(GboardFloatingStructureResolver.Structure structure) {
        if (structure == null || structure.keyboardArea == null) return;
        Claim claim;
        synchronized (LOCK) {
            claim = BY_BASE.remove(structure.keyboardArea);
        }
        if (claim == null) return;
        try {
            ViewTreeObserver observer = claim.structure.keyboardArea.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(claim.preDrawListener);
        } catch (Throwable ignored) {}

        for (Map.Entry<View, Drawable> entry : claim.backgrounds.entrySet()) {
            View view = entry.getKey();
            if (view == null) continue;
            try { view.setBackground(entry.getValue()); } catch (Throwable ignored) {}
        }
        try { claim.structure.keyboardArea.setElevation(claim.baseElevation); }
        catch (Throwable ignored) {}
        try { claim.structure.stockBackground.setAlpha(claim.stockAlpha); }
        catch (Throwable ignored) {}
        claim.backgrounds.clear();
    }

    private static void applyClaim(Claim claim) {
        if (claim == null) return;
        GboardFloatingStructureResolver.Structure structure = claim.structure;
        suppressBackground(claim, structure.keyboardArea);
        try { structure.keyboardArea.setElevation(0f); } catch (Throwable ignored) {}
        try { structure.stockBackground.setAlpha(0f); } catch (Throwable ignored) {}
        suppressBackground(claim, structure.contentColumn);
        suppressBackground(claim, structure.keyboardHolder);
        suppressBackground(claim, structure.bottomFrame);
        View topEdge = structure.topEdge;
        if (topEdge != null) suppressBackground(claim, topEdge);
        for (ViewGroup holder : structure.keyboardViewHolders) {
            suppressBackground(claim, holder);
            suppressCurrentContentBackgrounds(claim, holder);
        }
    }

    private static void suppressBackground(Claim claim, View view) {
        if (claim == null || view == null) return;
        claim.rememberBackground(view);
        try {
            if (view.getBackground() != null) view.setBackground(null);
        } catch (Throwable ignored) {}
    }

    /**
     * KeyboardViewHolder content is dynamically rebound by Gboard. Only direct content surfaces
     * that fill the holder are owned; descendants such as keycaps, icons and ripple drawables are
     * deliberately untouched.
     */
    private static void suppressCurrentContentBackgrounds(Claim claim, ViewGroup holder) {
        if (claim == null || holder == null) return;
        int holderWidth = holder.getWidth();
        int holderHeight = holder.getHeight();
        for (int i = 0; i < holder.getChildCount(); i++) {
            View content = holder.getChildAt(i);
            if (content == null) continue;
            boolean softKeyboard = SOFT_KEYBOARD_VIEW_CLASS.equals(content.getClass().getName());
            boolean fillsHolder = holderWidth > 0 && holderHeight > 0
                    && content.getWidth() >= Math.max(1, holderWidth / 2)
                    && content.getHeight() >= Math.max(1, holderHeight / 2);
            if (!softKeyboard && !fillsHolder) continue;
            suppressBackground(claim, content);
        }
    }
}
