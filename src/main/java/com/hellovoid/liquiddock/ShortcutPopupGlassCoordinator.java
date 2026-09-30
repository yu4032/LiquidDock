package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
        View content = state.contentRef.get();
        if (content == null || !content.isAttachedToWindow()) return false;
        ViewParent parent = content.getParent();
        if (!(parent instanceof ViewGroup)) return false;

        // The stock ShortcutMenu material is owned by PopupView.getContentView(), but mounting our
        // TextureView *inside* that View applies contentView's scale twice. Instead, become the
        // content's immediate sibling in the same menu_layer and mirror the exact local frame and
        // transform. PopupView remains the sole authority for placement/animation.
        ViewGroup host = (ViewGroup) parent;
        int contentIndex = host.indexOfChild(content);
        if (contentIndex < 0) return false;

        int width = Math.max(1, content.getWidth() > 0
                ? content.getWidth() : content.getMeasuredWidth());
        int height = Math.max(1, content.getHeight() > 0
                ? content.getHeight() : content.getMeasuredHeight());
        ShortcutPopupGlassLayer layer = new ShortcutPopupGlassLayer(
                content.getContext(), state.session);
        host.addView(layer, contentIndex, new ViewGroup.LayoutParams(width, height));
        state.layer = layer;
        syncLayerToContent(state);
        updateGeometry(state);
        MainHook.log(TAG + " content-sibling output inserted"
                + " host=" + host.getClass().getName()
                + " index=" + contentIndex
                + " size=" + width + "x" + height
                + " backdropReady=" + state.session.hasFrozenBackdrop());
        return true;
    }

    private static void syncLayerToContent(State state) {
        if (state == null || state.released) return;
        View content = state.contentRef.get();
        ShortcutPopupGlassLayer layer = state.layer;
        if (content == null || layer == null) return;

        int width = Math.max(1, content.getWidth() > 0
                ? content.getWidth() : content.getMeasuredWidth());
        int height = Math.max(1, content.getHeight() > 0
                ? content.getHeight() : content.getMeasuredHeight());
        ViewGroup.LayoutParams lp = layer.getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            layer.setLayoutParams(lp);
        }

        // The sibling is laid out at the host origin. Recreate content's local frame and transform
        // exactly once; do not copy alpha because ShortcutPopupGlassLayer owns reveal/dismiss alpha.
        layer.setTranslationX(
                (content.getLeft() - layer.getLeft()) + content.getTranslationX());
        layer.setTranslationY(
                (content.getTop() - layer.getTop()) + content.getTranslationY());
        layer.setPivotX(content.getPivotX());
        layer.setPivotY(content.getPivotY());
        layer.setScaleX(content.getScaleX());
        layer.setScaleY(content.getScaleY());
        layer.setRotation(content.getRotation());
        layer.setRotationX(content.getRotationX());
        layer.setRotationY(content.getRotationY());
    }

    private static void updateGeometry(State state) {
        if (state == null || state.released) return;
        syncLayerToContent(state);
        View decor = state.popupDecorRef.get();
        View content = state.contentRef.get();
        ShortcutPopupGlassSession session = state.session;
        if (decor == null || content == null || session == null || decor.getWidth() <= 0
                || decor.getHeight() <= 0 || !content.isAttachedToWindow()) return;
        Rect rect = new Rect();
        if (!content.getGlobalVisibleRect(rect) || rect.width() <= 0 || rect.height() <= 0) return;
        int[] root = new int[2];
        decor.getLocationOnScreen(root);
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                root[0], root[1], rect.left, rect.top, rect.right, rect.bottom);
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolve(
                decor.getWidth(), decor.getHeight(),
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                resolveShortcutMenuCornerRadius(content));
        ShortcutPopupGlassLayer layer = state.layer;
        int[] layerScreen = new int[2];
        int layerWidth = -1;
        int layerHeight = -1;
        if (layer != null) {
            try {
                layer.getLocationOnScreen(layerScreen);
                layerWidth = layer.getWidth();
                layerHeight = layer.getHeight();
            } catch (Throwable ignored) {}
        }
        MainHook.log(TAG + " [YDIAG] geometry"
                + " contentGlobal=" + rect.toShortString()
                + " decorScreen=" + root[0] + "," + root[1]
                + " decorSize=" + decor.getWidth() + "x" + decor.getHeight()
                + " relative=" + bounds.left + "," + bounds.top
                + "-" + bounds.right + "," + bounds.bottom
                + " layerScreen=" + layerScreen[0] + "," + layerScreen[1]
                + " layerSize=" + layerWidth + "x" + layerHeight
                + (geometry != null
                        ? " prismalCenter=" + geometry.centerX + "," + geometry.centerY
                            + " prismalSize=" + geometry.width + "x" + geometry.height
                        : " prismal=null"));
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
