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
        State state = new State(
                decorView, popupView, contentView, glassConfig, radius);

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

        if (hasRealGeometry(contentView)) {
            if (!activateLocked(state, contentView)) {
                releaseLocked("initial-bind-failed");
                return false;
            }
            return true;
        }

        // ShortcutMenu.show() adds PopupView to the hierarchy before the first layout. Device
        // logs confirm mContentView is attached but still 0x0 at that boundary. Backdrop effects
        // are RenderNode properties, so wait for the first non-zero layout rather than creating
        // the effect against a 0x0 node and hoping later invalidation repairs its native state.
        View.OnLayoutChangeListener layoutListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (right <= left || bottom <= top) return;
                    synchronized (ShortcutPopupGlassCoordinator.class) {
                        if (current != state || state.released || state.activated) return;
                        if (!activateLocked(state, v)) {
                            releaseLocked("layout-bind-failed");
                        }
                    }
                };
        state.layoutListener = layoutListener;
        contentView.addOnLayoutChangeListener(layoutListener);
        MainHook.log(TAG + " waiting for popup layout target="
                + contentView.getClass().getName());
        return true;
    }

    private static boolean activateLocked(State state, View contentView) {
        if (state == null || state.released || state.activated || current != state
                || contentView == null || !contentView.isAttachedToWindow()
                || !hasRealGeometry(contentView)) {
            return false;
        }
        View popupView = state.popupRef.get();
        if (popupView == null || !popupView.isAttachedToWindow()) return false;

        if (state.layoutListener != null) {
            try { contentView.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }

        ShortcutPopupHwuiGlassEffect effect = ShortcutPopupHwuiGlassEffect.attach(
                contentView, state.glassConfig, state.cornerRadius);
        if (effect == null) return false;

        ShortcutPopupVendorMaterialBridge.Claim materialClaim =
                ShortcutPopupVendorMaterialBridge.claim(popupView, contentView);
        if (materialClaim == null) {
            effect.dispose();
            return false;
        }

        state.effect = effect;
        state.materialClaim = materialClaim;
        state.activated = true;
        contentView.invalidate();
        MainHook.log(TAG + " HWUI backdrop effect bound size="
                + contentView.getWidth() + "x" + contentView.getHeight()
                + " radius=" + state.cornerRadius);
        return true;
    }

    private static boolean hasRealGeometry(View view) {
        return view != null && view.getWidth() > 0 && view.getHeight() > 0;
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

        View content = state.contentRef.get();
        if (content != null && state.layoutListener != null) {
            try { content.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
        }
        state.layoutListener = null;

        if (state.effect != null) {
            state.effect.dispose();
        }
        if (state.materialClaim != null) {
            ShortcutPopupVendorMaterialBridge.restoreIfVisible(state.materialClaim);
        }
        MainHook.log(TAG + " released reason=" + reason
                + " activated=" + state.activated);
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
        final LiquidDockConfig.Glass glassConfig;
        final float cornerRadius;
        ShortcutPopupHwuiGlassEffect effect;
        ShortcutPopupVendorMaterialBridge.Claim materialClaim;
        View.OnAttachStateChangeListener detachListener;
        View.OnLayoutChangeListener layoutListener;
        boolean activated;
        boolean released;

        State(
                View decor,
                View popup,
                View content,
                LiquidDockConfig.Glass glassConfig,
                float cornerRadius) {
            decorRef = new WeakReference<>(decor);
            popupRef = new WeakReference<>(popup);
            contentRef = new WeakReference<>(content);
            this.glassConfig = glassConfig;
            this.cornerRadius = cornerRadius;
        }
    }
}
