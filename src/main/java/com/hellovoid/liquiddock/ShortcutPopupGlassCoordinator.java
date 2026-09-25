package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/** Coordinates pre-show workspace capture and stable root-wide ShortcutMenu glass presentation. */
final class ShortcutPopupGlassCoordinator {
    private static final String TAG = "[DC][ShortcutPopupGlass]";
    private static State current;

    private ShortcutPopupGlassCoordinator() {}

    static synchronized void armTouch(View captureRoot, LiquidDockConfig.Glass glassConfig) {
        prepareInternal(captureRoot, glassConfig, false, "touch-arm-replace");
    }

    /**
     * Preserve the published ShortcutMenu root contract for Dock: setRequestingItemInfo() runs on
     * the actual ShortcutMenuLayer that will later become ShortcutMenu.mDecorView. Do not infer
     * this authority from Dock touch routing or from the pressed icon's root.
     */
    static synchronized void prepareAuthoritativeRoot(
            View captureRoot, LiquidDockConfig.Glass glassConfig) {
        prepareInternal(captureRoot, glassConfig, true, "authoritative-root-replace");
    }

    private static void prepareInternal(
            View captureRoot,
            LiquidDockConfig.Glass glassConfig,
            boolean authoritativeFirstFrame,
            String replaceReason) {
        releaseLocked(replaceReason);
        if (captureRoot == null || glassConfig == null || !GlassRuntimeState.isEnabled()
                || !captureRoot.isAttachedToWindow()) return;
        State state = new State(captureRoot, glassConfig, authoritativeFirstFrame);
        if (authoritativeFirstFrame) {
            // This mode intentionally restores the published Dock behavior: the actual menu root
            // is authoritative, and the first frame from that root is the frozen backdrop.
            state.latched = true;
        }
        current = state;
        ShortcutPopupSourceOverlay overlay = ShortcutPopupSourceOverlay.attach(
                captureRoot,
                new ShortcutPopupSourceOverlay.Listener() {
                    @Override public void onAttached(ShortcutPopupSourceOverlay attached) {
                        startSession(state, attached);
                    }

                    @Override public void onAttachFailed(Throwable error) {
                        MainHook.log(TAG + " source overlay attach failed: " + error);
                        release(state, "source-attach-failed");
                    }
                });
        state.sourceOverlay = overlay;
        if (overlay == null) releaseLocked("source-overlay-unavailable");
    }

    private static void startSession(State state, ShortcutPopupSourceOverlay sourceRoot) {
        synchronized (ShortcutPopupGlassCoordinator.class) {
            if (current != state || state.released) return;
            state.session = new ShortcutPopupGlassSession(
                    sourceRoot,
                    state.glassConfig,
                    new ShortcutPopupGlassSession.Listener() {
                        @Override public void onPresented() {
                            ShortcutPopupGlassCoordinator.onPresented(state);
                        }

                        @Override public void onFailure(Throwable error) {
                            MainHook.log(TAG + " session failed: " + error);
                            release(state, "session-failure");
                        }
                    });
            if (state.authoritativeFirstFrame) {
                state.session.captureFirstFrameAndFreeze();
                boolean outputReady = ensurePopupOutput(state);
                MainHook.log(TAG + " authoritative menu-root capture active popupReady="
                        + outputReady);
            } else {
                state.session.beginPrewarm();
                MainHook.log(TAG + " touch prewarm active");
            }
        }
    }

    /**
     * Semantic commit boundary from Launcher.dragSingleItem(): this runs before
     * Workspace.startDrag() can create DragView/edit-state mutations.
     */
    static synchronized boolean latchBeforeDrag(View captureRoot) {
        State state = current;
        if (state == null || state.released || state.captureRootRef.get() != captureRoot
                || state.session == null || state.latched) {
            return state != null && state.latched;
        }
        boolean latched = state.session.latchPreDragBackdrop();
        if (!latched) {
            releaseLocked("pre-drag-latch-miss");
            return false;
        }
        state.latched = true;
        MainHook.log(TAG + " clean backdrop committed before drag");
        return true;
    }

    static synchronized void cancelTouchIfUnlatched(View captureRoot, String reason) {
        State state = current;
        if (state == null || state.released || state.captureRootRef.get() != captureRoot
                || state.latched || state.contentRef.get() != null) {
            return;
        }
        releaseLocked(reason != null ? reason : "touch-ended");
    }

    static synchronized boolean bindPopup(View decorView, View popupView, View contentView) {
        State state = current;
        View liveRoot = decorView != null ? decorView.getRootView() : null;
        if (state == null || state.released || !state.latched
                || state.captureRootRef.get() != liveRoot
                || popupView == null || !(contentView instanceof ViewGroup)
                || !(decorView instanceof ViewGroup)) {
            return false;
        }
        if (!state.authoritativeFirstFrame
                && (state.session == null || !state.session.hasFrozenBackdrop())) {
            return false;
        }

        state.popupDecorRef = new WeakReference<>(decorView);
        state.popupRef = new WeakReference<>(popupView);
        state.contentRef = new WeakReference<>(contentView);

        if (state.popupDetachListener == null) {
            View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}

                @Override public void onViewDetachedFromWindow(View v) {
                    // HyperOS 4.50 PopupView dismisses by calling decor.removeView(popup) from the
                    // animation-end callback. Do not synchronously remove our sibling layer/window
                    // from inside that ViewGroup removal traversal; finish vendor removal first.
                    postDismissCleanup(state);
                }
            };
            state.popupDetachListener = detachListener;
            popupView.addOnAttachStateChangeListener(detachListener);
        }

        if (state.preDrawListener == null) {
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            ViewTreeObserver.OnPreDrawListener listener = () -> {
                updateGeometry(state);
                return true;
            };
            if (observer.isAlive()) {
                observer.addOnPreDrawListener(listener);
                state.observer = observer;
                state.preDrawListener = listener;
            }
        }

        boolean outputReady = ensurePopupOutput(state);
        updateGeometry(state);
        MainHook.log(TAG + " popup accepted mode="
                + (state.authoritativeFirstFrame ? "authoritative-root" : "pre-drag-latch")
                + " outputReady=" + outputReady
                + " sessionReady=" + (state.session != null)
                + " backdropReady="
                + (state.session != null && state.session.hasFrozenBackdrop()));
        return true;
    }

    private static boolean ensurePopupOutput(State state) {
        if (state == null || state.released || state.layer != null || state.session == null) {
            return state != null && state.layer != null;
        }
        View popup = state.popupRef.get();
        View content = state.contentRef.get();
        if (popup == null || !popup.isAttachedToWindow() || !(content instanceof ViewGroup)
                || !content.isAttachedToWindow()) {
            return false;
        }

        ViewGroup contentGroup = (ViewGroup) content;
        ShortcutPopupGlassLayer layer = new ShortcutPopupGlassLayer(
                content.getContext(), state.session);
        // This is a replacement for the native material background, not a sibling presentation
        // layer. Index 0 keeps all Launcher menu content above it while letting MIUIX's existing
        // mContentView bounds, alpha and corner-radius animation remain authoritative.
        contentGroup.addView(layer, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        state.layer = layer;
        updateGeometry(state);
        MainHook.log(TAG + " local popup output inserted in content index=0"
                + " size=" + content.getWidth() + "x" + content.getHeight()
                + " backdropReady=" + state.session.hasFrozenBackdrop());
        return true;
    }

    private static void updateGeometry(State state) {
        if (state == null || state.released) return;
        View captureRoot = state.captureRootRef.get();
        View content = state.contentRef.get();
        ShortcutPopupGlassSession session = state.session;
        if (captureRoot == null || content == null || session == null
                || captureRoot.getWidth() <= 0 || captureRoot.getHeight() <= 0
                || !content.isAttachedToWindow()) return;
        Rect rect = new Rect();
        if (!content.getGlobalVisibleRect(rect) || rect.width() <= 0 || rect.height() <= 0) return;
        int[] root = new int[2];
        captureRoot.getLocationOnScreen(root);
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                root[0], root[1], rect.left, rect.top, rect.right, rect.bottom);
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolve(
                captureRoot.getWidth(), captureRoot.getHeight(),
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                resolveShortcutMenuCornerRadius(content));
        if (geometry != null) session.updateGeometry(geometry);
    }

    private static void onPresented(State state) {
        synchronized (ShortcutPopupGlassCoordinator.class) {
            if (current != state || state.released || state.materialClaimed) return;
            View content = state.contentRef.get();
            ShortcutPopupGlassLayer layer = state.layer;
            if (content == null || layer == null) return;
            MiBlurBridge.clearContentBlur(content);
            content.setBackgroundColor(Color.TRANSPARENT);
            content.setElevation(0f);
            state.materialClaimed = true;
            layer.reveal();
            MainHook.log(TAG + " workspace-backed popup glass presented; vendor material released");
        }
    }

    static synchronized void cancelPending(View captureRoot) {
        State state = current;
        if (state != null && state.captureRootRef.get() == captureRoot
                && state.contentRef.get() == null) {
            releaseLocked("query-cancelled");
        }
    }

    static synchronized void releasePopupIfDetached(View decorView, View popupView) {
        State state = current;
        View liveRoot = decorView != null ? decorView.getRootView() : null;
        if (state == null || state.captureRootRef.get() != liveRoot) return;
        if (popupView == null || !popupView.isAttachedToWindow()) releaseLocked("popup-dismissed");
    }

    private static void postDismissCleanup(State state) {
        if (state == null || state.released || state.dismissCleanupPosted) return;
        state.dismissCleanupPosted = true;
        View decor = state.popupDecorRef.get();
        if (decor != null) {
            decor.post(() -> release(state, "popup-detached"));
            return;
        }
        View captureRoot = state.captureRootRef.get();
        if (captureRoot != null) {
            captureRoot.post(() -> release(state, "popup-detached"));
            return;
        }
        release(state, "popup-detached-no-root");
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
        if (popup != null && state.popupDetachListener != null) {
            try { popup.removeOnAttachStateChangeListener(state.popupDetachListener); }
            catch (Throwable ignored) {}
        }
        if (state.observer != null && state.preDrawListener != null) {
            try {
                if (state.observer.isAlive()) {
                    state.observer.removeOnPreDrawListener(state.preDrawListener);
                }
            } catch (Throwable ignored) {}
        }
        state.observer = null;
        state.preDrawListener = null;
        ShortcutPopupGlassLayer layer = state.layer;
        state.layer = null;
        if (layer != null) layer.dispose();
        ShortcutPopupGlassSession session = state.session;
        state.session = null;
        if (session != null) session.shutdown();
        ShortcutPopupSourceOverlay source = state.sourceOverlay;
        state.sourceOverlay = null;
        if (source != null) source.dispose();
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
        final WeakReference<View> captureRootRef;
        final LiquidDockConfig.Glass glassConfig;
        final boolean authoritativeFirstFrame;
        WeakReference<View> popupDecorRef = new WeakReference<>(null);
        WeakReference<View> popupRef = new WeakReference<>(null);
        WeakReference<View> contentRef = new WeakReference<>(null);
        ShortcutPopupSourceOverlay sourceOverlay;
        ShortcutPopupGlassSession session;
        ShortcutPopupGlassLayer layer;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        View.OnAttachStateChangeListener popupDetachListener;
        boolean latched;
        boolean materialClaimed;
        boolean dismissCleanupPosted;
        boolean released;

        State(
                View captureRoot,
                LiquidDockConfig.Glass glassConfig,
                boolean authoritativeFirstFrame) {
            captureRootRef = new WeakReference<>(captureRoot);
            this.glassConfig = glassConfig;
            this.authoritativeFirstFrame = authoritativeFirstFrame;
        }
    }
}
