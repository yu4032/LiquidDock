package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/**
 * Renders ShortcutMenu glass from the Launcher workspace PassBlur authority into the popup
 * ViewRoot. Source and output intentionally live in different ViewRoots, so the popup output can
 * never feed back into the workspace producer.
 */
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
                || !GlassRuntimeState.isEnabled() || !decorView.isAttachedToWindow()
                || !popupView.isAttachedToWindow() || !contentView.isAttachedToWindow()) {
            return false;
        }

        float radius = resolveShortcutMenuCornerRadius(contentView);
        State state = new State(decorView, popupView, contentView, glassConfig, radius);

        View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                // PopupView removes itself at the end of its own animator. Defer sibling output
                // teardown until vendor ViewGroup removal has completed.
                View decor = state.decorRef.get();
                if (decor != null) decor.post(() -> release(state, "popup-detached"));
                else release(state, "popup-detached-no-decor");
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

        View.OnLayoutChangeListener layoutListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (right <= left || bottom <= top) return;
                    synchronized (ShortcutPopupGlassCoordinator.class) {
                        if (current != state || state.released || state.bound) return;
                        if (!activateLocked(state, v)) releaseLocked("layout-bind-failed");
                    }
                };
        state.layoutListener = layoutListener;
        contentView.addOnLayoutChangeListener(layoutListener);
        MainHook.log(TAG + " waiting for popup layout target="
                + contentView.getClass().getName());
        return true;
    }

    private static boolean activateLocked(State state, View contentView) {
        if (state == null || state.released || state.bound || current != state
                || contentView == null || !contentView.isAttachedToWindow()
                || !hasRealGeometry(contentView)) {
            return false;
        }
        View decorView = state.decorRef.get();
        View popupView = state.popupRef.get();
        if (decorView == null || popupView == null || !decorView.isAttachedToWindow()
                || !popupView.isAttachedToWindow()) {
            return false;
        }

        if (state.layoutListener != null) {
            try { contentView.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }

        View launcherRoot = LauncherGlassSessionRegistry.resolveStableRoot(decorView);
        LauncherGlassSession authority =
                LauncherGlassSessionRegistry.acquire(decorView, state.glassConfig);
        if (launcherRoot == null || authority == null || authority.isShutdown()
                || !authority.ownsRoot(launcherRoot)) {
            MainHook.log(TAG + " launcher workspace authority unavailable");
            return false;
        }

        LauncherGlassSinkView sink = LauncherGlassSinkView.attachToExternalMaterial(
                contentView, authority, state.cornerRadius, state.glassConfig);
        if (sink == null) {
            MainHook.log(TAG + " external popup sink unavailable");
            return false;
        }

        state.launcherRootRef = new WeakReference<>(launcherRoot);
        state.authority = authority;
        state.sink = sink;
        state.bound = true;
        sink.setNodeKind(LauncherGlassNodeKind.LARGE_FOLDER);
        sink.runWhenOutputLost(() -> release(state, "output-lost"));
        sink.runWhenFirstFramePresented(() -> onFirstFramePresented(state));

        installPopupGeometryDriver(state, popupView);
        syncExternalGeometry(state);

        // Keep the stock popup material visible until the external sink has actually presented
        // its first frame. The fresh request updates the shared Launcher source without ever
        // sampling the popup ViewRoot that contains this output TextureView.
        authority.requestFreshBackdrop();

        MainHook.log(TAG + " external Prismal sink bound size="
                + contentView.getWidth() + "x" + contentView.getHeight()
                + " radius=" + state.cornerRadius
                + " source=" + authority.diagnosticSessionId());
        return true;
    }

    private static void installPopupGeometryDriver(State state, View popupView) {
        ViewTreeObserver observer = popupView.getViewTreeObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            syncExternalGeometry(state);
            return true;
        };
        state.geometryObserver = observer;
        state.geometryListener = listener;
        observer.addOnPreDrawListener(listener);
    }

    private static void syncExternalGeometry(State state) {
        if (state == null || state.released || !state.bound || current != state) return;
        LauncherGlassSinkView sink = state.sink;
        LauncherGlassSession authority = state.authority;
        View launcherRoot = state.launcherRootRef.get();
        if (sink == null || authority == null || authority.isShutdown()
                || launcherRoot == null || !launcherRoot.isAttachedToWindow()) {
            return;
        }
        sink.syncFromMaterial();
        LauncherGlassGeometry.Snapshot geometry = sink.captureGeometry(launcherRoot);
        if (geometry != null) authority.publishDragGeometry(sink, geometry);
    }

    private static void onFirstFramePresented(State expected) {
        synchronized (ShortcutPopupGlassCoordinator.class) {
            if (current != expected || expected.released || !expected.bound
                    || expected.activated) {
                return;
            }
            View popupView = expected.popupRef.get();
            View contentView = expected.contentRef.get();
            if (popupView == null || contentView == null || !popupView.isAttachedToWindow()
                    || !contentView.isAttachedToWindow()) {
                releaseLocked("first-frame-owner-lost");
                return;
            }

            ShortcutPopupVendorMaterialBridge.Claim materialClaim =
                    ShortcutPopupVendorMaterialBridge.claim(popupView, contentView);
            if (materialClaim == null) {
                releaseLocked("material-claim-failed");
                return;
            }

            expected.materialClaim = materialClaim;
            expected.activated = true;
            contentView.invalidate();
            MainHook.log(TAG + " external Prismal first frame presented; vendor material released"
                    + " size=" + contentView.getWidth() + "x" + contentView.getHeight());
        }
    }

    private static boolean hasRealGeometry(View view) {
        return view != null && view.getWidth() > 0 && view.getHeight() > 0;
    }

    static synchronized void releasePopupIfDetached(View decorView, View popupView) {
        State state = current;
        if (state == null || state.decorRef.get() != decorView) return;
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

        if (state.geometryObserver != null && state.geometryListener != null) {
            try {
                if (state.geometryObserver.isAlive()) {
                    state.geometryObserver.removeOnPreDrawListener(state.geometryListener);
                }
            } catch (Throwable ignored) {}
        }
        state.geometryObserver = null;
        state.geometryListener = null;

        // Fail closed: restore vendor material before removing the custom output whenever the
        // popup is still visible.
        if (state.materialClaim != null) {
            ShortcutPopupVendorMaterialBridge.restoreIfVisible(state.materialClaim);
            state.materialClaim = null;
        }

        LauncherGlassSinkView sink = state.sink;
        state.sink = null;
        if (sink != null) sink.dispose();

        // The LauncherGlassSession belongs to the stable Launcher root registry and may serve
        // dock/folder/widget glass too. Never shut it down from a transient popup.
        state.authority = null;

        MainHook.log(TAG + " released reason=" + reason
                + " bound=" + state.bound + " activated=" + state.activated);
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
        WeakReference<View> launcherRootRef = new WeakReference<>(null);
        final LiquidDockConfig.Glass glassConfig;
        final float cornerRadius;
        LauncherGlassSession authority;
        LauncherGlassSinkView sink;
        ShortcutPopupVendorMaterialBridge.Claim materialClaim;
        View.OnAttachStateChangeListener detachListener;
        View.OnLayoutChangeListener layoutListener;
        ViewTreeObserver geometryObserver;
        ViewTreeObserver.OnPreDrawListener geometryListener;
        boolean bound;
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
