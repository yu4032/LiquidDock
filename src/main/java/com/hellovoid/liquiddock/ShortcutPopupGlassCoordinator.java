package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.ViewParent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/** Coordinates pre-show workspace capture and stable root-wide ShortcutMenu glass presentation. */
final class ShortcutPopupGlassCoordinator {
    private static final String TAG = "[DC][ShortcutPopupGlass]";
    private static State current;

    private ShortcutPopupGlassCoordinator() {}

    static synchronized void prepare(View captureRoot, LiquidDockConfig.Glass glassConfig) {
        prepareInternal(captureRoot, glassConfig, false, null, "prepare-replace");
    }

    static synchronized void prepareEarly(
            View captureRoot, LiquidDockConfig.Glass glassConfig) {
        prepareInternal(captureRoot, glassConfig, true, null, "early-prepare-replace");
    }

    static synchronized void prepareDockEarly(
            View dockMenuOwner, LiquidDockConfig.Glass glassConfig) {
        if (dockMenuOwner == null) return;
        State state = current;
        if (state != null && !state.released && state.contentRef.get() != null) {
            return;
        }
        prepareInternal(
                dockMenuOwner.getRootView(),
                glassConfig,
                true,
                dockMenuOwner,
                "dock-early-prepare-replace");
    }

    static synchronized void prepareIfNeeded(
            View authorityOwner,
            View captureRoot,
            LiquidDockConfig.Glass glassConfig) {
        State state = current;
        if (state != null && !state.released && state.early) {
            View earlyOwner = state.earlyOwnerRef.get();
            boolean workspaceMatch = earlyOwner == null
                    && state.captureRootRef.get() == captureRoot;
            boolean dockMatch = matchesDockAuthority(state, authorityOwner);
            if (workspaceMatch || dockMatch) {
                state.requestStarted = true;
                return;
            }
        }
        prepareInternal(captureRoot, glassConfig, false, null, "prepare-replace");
        state = current;
        if (state != null && state.captureRootRef.get() == captureRoot) {
            state.requestStarted = true;
        }
    }

    static synchronized void cancelEarlyIfUnused(View captureRoot) {
        State state = current;
        if (state == null || state.released || !state.early
                || state.earlyOwnerRef.get() != null
                || state.captureRootRef.get() != captureRoot
                || state.requestStarted || state.contentRef.get() != null) {
            return;
        }
        releaseLocked("early-touch-ended-unused");
    }

    static synchronized void cancelDockEarlyIfUnused(View dockMenuOwner) {
        State state = current;
        if (state == null || state.released || !state.early
                || state.earlyOwnerRef.get() != dockMenuOwner
                || state.requestStarted || state.contentRef.get() != null) {
            return;
        }
        releaseLocked("dock-early-touch-ended-unused");
    }

    private static void prepareInternal(
            View captureRoot,
            LiquidDockConfig.Glass glassConfig,
            boolean early,
            View earlyOwner,
            String replaceReason) {
        releaseLocked(replaceReason);
        if (captureRoot == null || glassConfig == null || !GlassRuntimeState.isEnabled()
                || !captureRoot.isAttachedToWindow()) return;
        State state = new State(captureRoot, glassConfig, early, earlyOwner);
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
            state.session.requestInitialCapture();
            boolean outputReady = ensurePopupOutput(state);
            MainHook.log(TAG + " pre-show workspace capture requested popupReady=" + outputReady);
        }
    }

    static synchronized boolean bindPopup(View decorView, View popupView, View contentView) {
        State state = current;
        View liveRoot = decorView != null ? decorView.getRootView() : null;
        boolean rootMatches = state != null && state.captureRootRef.get() == liveRoot;
        boolean dockOwnerMatches = state != null
                && state.requestStarted
                && matchesDockAuthority(state, decorView);
        if (state == null || state.released || (!rootMatches && !dockOwnerMatches)
                || popupView == null || contentView == null || !(decorView instanceof ViewGroup)) {
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
        MainHook.log(TAG + " popup accepted outputReady=" + outputReady
                + " sessionReady=" + (state.session != null));
        return true;
    }

    private static boolean ensurePopupOutput(State state) {
        if (state == null || state.released || state.layer != null || state.session == null) {
            return state != null && state.layer != null;
        }
        View decor = state.popupDecorRef.get();
        View popup = state.popupRef.get();
        if (!(decor instanceof ViewGroup) || popup == null || !popup.isAttachedToWindow()) {
            return false;
        }
        ViewGroup decorGroup = (ViewGroup) decor;
        int popupIndex = decorGroup.indexOfChild(popup);
        if (popupIndex < 0) return false;

        ShortcutPopupGlassLayer layer = new ShortcutPopupGlassLayer(
                decor.getContext(), state.session);
        decorGroup.addView(layer, popupIndex, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        state.layer = layer;
        updateGeometry(state);
        MainHook.log(TAG + " stable full-screen output inserted below PopupView index=" + popupIndex
                + " backdropReady=" + state.session.hasFrozenBackdrop());
        return true;
    }

    private static void updateGeometry(State state) {
        if (state == null || state.released) return;
        View decor = state.popupDecorRef.get();
        View content = state.contentRef.get();
        ShortcutPopupGlassSession session = state.session;
        if (decor == null || content == null || session == null || decor.getWidth() <= 0
                || decor.getHeight() <= 0 || !content.isAttachedToWindow()) return;
        if (content.getWidth() <= 0 || content.getHeight() <= 0) return;

        // ShortcutMenuPosition defines the layout-space frame. MiuiX then applies the material
        // host's local Matrix (pivot/scale) before composition. Reconstruct that visual frame in
        // decor/root coordinates with floats so the matrix is applied exactly once and without
        // getGlobalVisibleRect() clipping/rounding.
        float[] corners = {
                0f, 0f,
                content.getWidth(), 0f,
                0f, content.getHeight(),
                content.getWidth(), content.getHeight()
        };
        View current = content;
        while (current != decor) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return;
            View parentView = (View) parent;
            current.getMatrix().mapPoints(corners);
            float dx = current.getLeft() - parentView.getScrollX();
            float dy = current.getTop() - parentView.getScrollY();
            for (int i = 0; i < corners.length; i += 2) {
                corners[i] += dx;
                corners[i + 1] += dy;
            }
            current = parentView;
        }

        float left = corners[0];
        float top = corners[1];
        float right = corners[0];
        float bottom = corners[1];
        for (int i = 2; i < corners.length; i += 2) {
            left = Math.min(left, corners[i]);
            top = Math.min(top, corners[i + 1]);
            right = Math.max(right, corners[i]);
            bottom = Math.max(bottom, corners[i + 1]);
        }
        float radiusScale = Math.min(
                (right - left) / Math.max(1f, content.getWidth()),
                (bottom - top) / Math.max(1f, content.getHeight()));
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolveStatic(
                decor.getWidth(), decor.getHeight(),
                left, top, right, bottom,
                resolveShortcutMenuCornerRadius(content) * Math.max(0f, radiusScale));
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

    static synchronized void beginDismissFade(Object menu) {
        State state = current;
        if (state == null || state.released || menu == null) return;
        try {
            Object popupObject = HookUtil.getField(menu, "mPopupView");
            View popup = state.popupRef.get();
            if (popup == null || popupObject != popup) return;
        } catch (Throwable error) {
            MainHook.log(TAG + " dismiss fade owner check failed: " + error);
            return;
        }
        ShortcutPopupGlassLayer layer = state.layer;
        if (layer != null) {
            layer.fadeOutFast();
            MainHook.log(TAG + " fast dismiss fade started");
        }
    }

    static synchronized void cancelPending(View authorityOwner, View captureRoot) {
        State state = current;
        if (state == null || state.contentRef.get() != null) return;
        boolean rootMatches = state.captureRootRef.get() == captureRoot;
        boolean ownerMatches = matchesDockAuthority(state, authorityOwner);
        if (rootMatches || ownerMatches) releaseLocked("query-cancelled");
    }

    static synchronized void releasePopupIfDetached(View decorView, View popupView) {
        State state = current;
        View liveRoot = decorView != null ? decorView.getRootView() : null;
        if (state == null) return;
        boolean rootMatches = state.captureRootRef.get() == liveRoot;
        boolean ownerMatches = matchesDockAuthority(state, decorView);
        if (!rootMatches && !ownerMatches) return;
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

    private static boolean matchesDockAuthority(State state, View candidate) {
        if (state == null || candidate == null || !state.dockEarly) return false;
        Class<?> authorityClass = state.earlyAuthorityClass;
        return authorityClass != null && authorityClass.isInstance(candidate);
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
        final boolean early;
        final boolean dockEarly;
        final Class<?> earlyAuthorityClass;
        final WeakReference<View> earlyOwnerRef;
        WeakReference<View> popupDecorRef = new WeakReference<>(null);
        WeakReference<View> popupRef = new WeakReference<>(null);
        WeakReference<View> contentRef = new WeakReference<>(null);
        ShortcutPopupSourceOverlay sourceOverlay;
        ShortcutPopupGlassSession session;
        ShortcutPopupGlassLayer layer;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        View.OnAttachStateChangeListener popupDetachListener;
        boolean requestStarted;
        boolean materialClaimed;
        boolean dismissCleanupPosted;
        boolean released;

        State(
                View captureRoot,
                LiquidDockConfig.Glass glassConfig,
                boolean early,
                View earlyOwner) {
            captureRootRef = new WeakReference<>(captureRoot);
            this.glassConfig = glassConfig;
            this.early = early;
            this.dockEarly = earlyOwner != null;
            this.earlyAuthorityClass = earlyOwner != null ? earlyOwner.getClass() : null;
            earlyOwnerRef = new WeakReference<>(earlyOwner);
        }
    }
}
