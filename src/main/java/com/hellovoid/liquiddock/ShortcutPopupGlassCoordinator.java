package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;

/** Owns the HWUI-native ShortcutMenu backdrop effect and its real popup detach boundary. */
final class ShortcutPopupGlassCoordinator {
    private static final String TAG = "[DC][ShortcutPopupGlass]";
    private static State current;

    private ShortcutPopupGlassCoordinator() {}

    static synchronized boolean bindPopup(
            View decorView,
            View popupView,
            View contentView,
            LiquidDockConfig.Glass glassConfig) {
        releaseLocked("bind-replace");
        if (decorView == null || popupView == null || contentView == null || glassConfig == null
                || !GlassRuntimeState.isEnabled() || !popupView.isAttachedToWindow()
                || !contentView.isAttachedToWindow()) {
            return false;
        }

        float radius = resolveShortcutMenuCornerRadius(contentView);
        ShortcutPopupHwuiGlassEffect effect = ShortcutPopupHwuiGlassEffect.attach(
                contentView, glassConfig, radius);
        if (effect == null) return false;

        State state = new State(decorView, popupView, contentView, effect);
        View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                // Let HyperOS finish PopupView removal traversal before touching blur state.
                View decor = state.decorRef.get();
                if (decor != null) {
                    decor.post(() -> release(state, "popup-detached"));
                } else {
                    release(state, "popup-detached-no-decor");
                }
            }
        };
        state.detachListener = detachListener;
        popupView.addOnAttachStateChangeListener(detachListener);
        current = state;
        MainHook.log(TAG + " HWUI backdrop effect bound radius=" + radius);
        return true;
    }

    static synchronized void releasePopupIfDetached(View decorView, View popupView) {
        State state = current;
        if (state == null) return;
        if (state.decorRef.get() != decorView) return;
        if (popupView == null || !popupView.isAttachedToWindow()) {
            releaseLocked("popup-dismissed");
        }
    }

    private static void release(State expected, String reason) {
        synchronized (ShortcutPopupGlassCoordinator.class) {
            if (current != expected) return;
            releaseLocked(reason);
        }
    }

    private static void releaseLocked(String reason) {
        State state = current;
        current = null;
        if (state == null || state.released) return;
        state.released = true;
        View popup = state.popupRef.get();
        if (popup != null && state.detachListener != null) {
            try { popup.removeOnAttachStateChangeListener(state.detachListener); }
            catch (Throwable ignored) {}
        }
        state.effect.dispose();
        MainHook.log(TAG + " released reason=" + reason);
    }

    private static float resolveShortcutMenuCornerRadius(View contentView) {
        try {
            int id = contentView.getResources().getIdentifier(
                    "shortcut_menu_angle_radius", "dimen", "com.miui.home");
            if (id != 0) return contentView.getResources().getDimension(id);
        } catch (Throwable ignored) {}
        return 16f * contentView.getResources().getDisplayMetrics().density;
    }

    private static final class State {
        final WeakReference<View> decorRef;
        final WeakReference<View> popupRef;
        final WeakReference<View> contentRef;
        final ShortcutPopupHwuiGlassEffect effect;
        View.OnAttachStateChangeListener detachListener;
        boolean released;

        State(View decor, View popup, View content, ShortcutPopupHwuiGlassEffect effect) {
            decorRef = new WeakReference<>(decor);
            popupRef = new WeakReference<>(popup);
            contentRef = new WeakReference<>(content);
            this.effect = effect;
        }
    }
}
