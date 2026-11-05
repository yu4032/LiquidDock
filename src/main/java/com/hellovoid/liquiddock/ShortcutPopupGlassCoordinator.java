package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.os.SystemClock;
import android.view.ViewParent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;

/** Coordinates pre-show workspace capture and stable root-wide ShortcutMenu glass presentation. */
final class ShortcutPopupGlassCoordinator {
    private static final String TAG = "[DC][ShortcutPopupGlass]";
    private static State current;
    // HWUI Workspace normally needs no extra popup producer on a simple icon tap.
    // Begin warming only for a sustained hold, before the native long-press timeout.
    private static final long WORKSPACE_PREWARM_LEAD_MS = 430L;
    private static final long HANDOFF_CANCEL_GRACE_MS = 1200L;
    // Never start the OEM entrance on a missing glass snapshot. Bounded native-only
    // fallback avoids a hung long press if capture/export fails.
    private static final long SHOW_GLASS_READY_TIMEOUT_MS = 700L;
    private static View pendingWorkspaceRoot;
    private static Runnable pendingWorkspacePrewarm;

    private ShortcutPopupGlassCoordinator() {}

    /** An installed Hook can switch at runtime; a visible popup is never re-captured in place. */
    static synchronized void onLivePopupGlassEnabled(boolean enabled) {
        if (!enabled) {
            cancelScheduledWorkspacePrewarm(null);
            releaseLocked("live-popup-glass-disabled");
        }
        // If enabled while a vendor popup is already visible, wait for the next popup.
        // Capturing now would recursively sample the popup's own foreground.
    }

    /** Existing popup sessions otherwise keep the optical config captured at construction. */
    static synchronized void onLiveGlassConfigChanged(LiquidDockConfig.Glass glassConfig) {
        State state = current;
        if (glassConfig == null || state == null || state.released) return;
        state.glassConfig = glassConfig;
        if (state.session != null) state.session.applyLiveGlassConfig(glassConfig);
        View content = state.contentRef.get();
        if (content != null) content.post(() -> updateGeometry(state));
    }

    static synchronized void prepare(View captureRoot, LiquidDockConfig.Glass glassConfig) {
        prepareInternal(captureRoot, glassConfig, false, null, "prepare-replace");
    }

    static synchronized void prepareEarly(
            View captureRoot, LiquidDockConfig.Glass glassConfig) {
        if (captureRoot == null) return;
        // Keep the TextureView fallback's proven immediate source path.
        if (!LauncherGlassStaticLayer.isHwuiProbeActive(captureRoot)) {
            prepareInternal(captureRoot, glassConfig, true, null, "early-prepare-replace");
            return;
        }
        // On the HWUI path, avoid a new PassBlur session for ordinary short taps.
        // Prepare before the long-press callback so the popup's FIRST frame can
        // already use the full desktop instead of switching mid-animation.
        if (pendingWorkspaceRoot == captureRoot && pendingWorkspacePrewarm != null) return;
        cancelScheduledWorkspacePrewarm(null);
        Runnable prewarm = () -> {
            synchronized (ShortcutPopupGlassCoordinator.class) {
                if (pendingWorkspaceRoot != captureRoot) return;
                pendingWorkspaceRoot = null;
                pendingWorkspacePrewarm = null;
                State live = current;
                if (live != null && !live.released && live.contentRef.get() != null) return;
                prepareInternal(captureRoot, glassConfig, true, null,
                        "workspace-hold-prewarm-replace");
            }
        };
        pendingWorkspaceRoot = captureRoot;
        pendingWorkspacePrewarm = prewarm;
        long delay = Math.max(0L,
                (long) ViewConfiguration.getLongPressTimeout() - WORKSPACE_PREWARM_LEAD_MS);
        if (!captureRoot.postDelayed(prewarm, delay)) {
            cancelScheduledWorkspacePrewarm(captureRoot);
        }
    }

    private static void cancelScheduledWorkspacePrewarm(View root) {
        if (pendingWorkspacePrewarm == null
                || (root != null && pendingWorkspaceRoot != root)) return;
        View pendingRoot = pendingWorkspaceRoot;
        if (pendingRoot != null) pendingRoot.removeCallbacks(pendingWorkspacePrewarm);
        pendingWorkspaceRoot = null;
        pendingWorkspacePrewarm = null;
    }

    static synchronized void prepareDockEarly(
            View dockMenuOwner, LiquidDockConfig.Glass glassConfig) {
        if (dockMenuOwner == null) return;
        // Dock icon long-press animations can start before the menu request. Unlike
        // Workspace's HWUI shortcut, the Dock must preserve a pre-animation scene.
        State state = current;
        if (state != null && !state.released) {
            if (state.contentRef.get() != null) return;
            // Both Dock touch routes can dispatch the same ACTION_DOWN. Replacing an
            // in-flight capture here would postpone freezing until the popup animates.
            if (state.early && state.dockEarly
                    && state.earlyOwnerRef.get() == dockMenuOwner
                    && state.captureRootRef.get() == dockMenuOwner.getRootView()) {
                return;
            }
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
        cancelScheduledWorkspacePrewarm(captureRoot);
        State state = current;
        if (state != null && !state.released && state.early) {
            View earlyOwner = state.earlyOwnerRef.get();
            boolean workspaceMatch = earlyOwner == null
                    && state.captureRootRef.get() == captureRoot;
            // setRequestingItemInfo belongs to ShortcutMenuLayer, not to
            // DockContainerView. Match the Launcher ViewRoot rather than requiring
            // the requesting View to have the Dock owner's Java class.
            boolean dockMatch = state.dockEarly
                    && state.captureRootRef.get() == captureRoot;
            if (workspaceMatch || dockMatch) {
                state.requestStarted = true;
                cancelHandoffCleanup(state);
                return;
            }
        }
        prepareInternal(captureRoot, glassConfig, false, null, "prepare-replace");
        state = current;
        if (state != null && state.captureRootRef.get() == captureRoot) {
            state.requestStarted = true;
        }
    }

    /**
     * The real ShortcutMenu.show() must not run until the offscreen glass material is
     * ready. Its entrance animation starts synchronously in the vendor method; merely
     * preparing the popup output after show() cannot guarantee a first-frame takeover.
     * The callback re-enters the hooked show on the UI thread with a one-shot bypass.
     */
    static synchronized boolean deferShowUntilGlassReady(Object menu, Runnable resumeShow) {
        State state = current;
        if (!BuildConfig.WORKSPACE_GLASS_HWUI_PROBE
                || menu == null || resumeShow == null || state == null || state.released
                || !state.requestStarted || state.contentRef.get() != null) return false;
        ShortcutPopupGlassLayer prepared = state.stagedLayer;
        if (prepared != null && prepared.hasPreShowHwuiBackdrop()) {
            MainHook.log(TAG + " native show admitted with ready pre-show glass ageMs="
                    + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
            return false;
        }
        if (state.deferredShow != null) {
            // Vendor may issue duplicate show() calls in one long-press cycle.
            return state.deferredMenu == menu;
        }
        View root = state.captureRootRef.get();
        if (root == null || !root.isAttachedToWindow()) return false;
        state.deferredMenu = menu;
        state.deferredShow = resumeShow;
        Runnable deadline = () -> {
            synchronized (ShortcutPopupGlassCoordinator.class) {
                if (current == state && !state.released && state.deferredShow != null) {
                    MainHook.log(TAG + " native-only fallback: glass not ready before show deadline"
                            + " ageMs=" + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
                    releaseLocked("show-preparation-timeout");
                }
            }
        };
        state.deferredShowTimeout = deadline;
        if (!root.postDelayed(deadline, SHOW_GLASS_READY_TIMEOUT_MS)) {
            takeDeferredShow(state);
            return false;
        }
        MainHook.log(TAG + " native show deferred until glass inputs are ready ageMs="
                + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
        return true;
    }

    /** Suppress a pending vendor show if the same menu was dismissed before presentation. */
    static synchronized void cancelDeferredShow(Object menu) {
        State state = current;
        if (state != null && state.deferredShow != null && state.deferredMenu == menu) {
            takeDeferredShow(state);
            releaseLocked("dismiss-before-native-show");
        }
    }

    private static Runnable takeDeferredShow(State state) {
        if (state == null) return null;
        Runnable resume = state.deferredShow;
        state.deferredShow = null;
        state.deferredMenu = null;
        Runnable timeout = state.deferredShowTimeout;
        state.deferredShowTimeout = null;
        View root = state.captureRootRef.get();
        if (timeout != null && root != null) root.removeCallbacks(timeout);
        return resume;
    }

    private static void resumeDeferredShowWithGlass(State state) {
        if (state == null || state.deferredShow == null || current != state
                || state.released || state.stagedLayer == null
                || !state.stagedLayer.hasPreShowHwuiBackdrop()) return;
        Runnable resume = takeDeferredShow(state);
        if (resume == null) return;
        MainHook.log(TAG + " glass ready; admitting native entrance ageMs="
                + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
        // onHwuiBackdropsReady is already on the main thread. Invoke immediately
        // rather than posting another task that could race dismiss/replacement.
        resume.run();
    }

    static synchronized void cancelEarlyIfUnused(View captureRoot) {
        cancelScheduledWorkspacePrewarm(captureRoot);
        State state = current;
        if (state == null || state.released || !state.early
                || state.earlyOwnerRef.get() != null
                || state.captureRootRef.get() != captureRoot
                || state.requestStarted || state.contentRef.get() != null) {
            return;
        }
        releaseLocked("early-touch-ended-unused");
    }

    /**
     * Native long-press ownership may send ACTION_CANCEL before it requests the menu.
     * Keep the already-frozen desktop across that handoff, but release it if no
     * menu request follows so drag-only gestures cannot retain a GPU producer.
     */
    static synchronized void deferEarlyCancelIfUnused(View root, View dockOwner) {
        if (dockOwner == null) cancelScheduledWorkspacePrewarm(root);
        State state = current;
        if (state == null || state.released || !state.early
                || state.captureRootRef.get() != root
                || state.earlyOwnerRef.get() != dockOwner
                || state.requestStarted || state.contentRef.get() != null
                || state.handoffCleanup != null) return;
        View anchor = state.captureRootRef.get();
        if (anchor == null) return;
        Runnable expire = () -> {
            synchronized (ShortcutPopupGlassCoordinator.class) {
                if (current == state && !state.released && !state.requestStarted
                        && state.contentRef.get() == null) {
                    releaseLocked("touch-cancel-handoff-unused");
                }
            }
        };
        state.handoffCleanup = expire;
        if (!anchor.postDelayed(expire, HANDOFF_CANCEL_GRACE_MS)) {
            state.handoffCleanup = null;
            releaseLocked("touch-cancel-detached");
        }
    }

    private static void cancelHandoffCleanup(State state) {
        if (state == null || state.handoffCleanup == null) return;
        View root = state.captureRootRef.get();
        if (root != null) root.removeCallbacks(state.handoffCleanup);
        state.handoffCleanup = null;
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

    /**
     * A drag-return alpha animation would redraw the Workspace glass every frame while the
     * shortcut popup is entering. Keep the normal drag fade everywhere else, but let this
     * short ownership handoff settle in one redraw.
     */
    static synchronized boolean shouldRestoreWorkspaceGlassImmediately(View root) {
        State state = current;
        return root != null && state != null && !state.released
                && state.captureRootRef.get() == root
                && (state.requestStarted || state.contentRef.get() != null);
    }

    private static void prepareInternal(
            View captureRoot,
            LiquidDockConfig.Glass glassConfig,
            boolean early,
            View earlyOwner,
            String replaceReason) {
        releaseLocked(replaceReason);
        LiquidDockConfig.Glass latest = LiveGlassConfigState.currentPopupGlass();
        if (latest != null) glassConfig = latest;
        else glassConfig = ScopedGlassOptics.resolve(
                ConfigReader.load(), glassConfig, ScopedGlassOptics.POPUP);
        if (captureRoot == null || glassConfig == null || !GlassRuntimeState.isEnabled()
                || !captureRoot.isAttachedToWindow()) return;
        State state = new State(captureRoot, glassConfig, early, earlyOwner);
        current = state;
        // Capture the already-visible desktop scene. Hiding the shared Workspace glass
        // for a single popup screenshot produces a visible black/transparent flash. The
        // popup's own dedicated source overlay handles capture independently.
        // The HWUI popup paints in Launcher coordinates. A fullscreen WindowManager
        // panel may have different system-bar insets, causing logicalWidth/Height
        // mismatch and a late TextureView fallback during the entrance.
        ShortcutPopupSourceOverlay overlay = ShortcutPopupSourceOverlay.attachToLauncherBounds(
                captureRoot,
                new ShortcutPopupSourceOverlay.Listener() {
                    @Override public void onAttached(ShortcutPopupSourceOverlay attached) {
                        View root = state.captureRootRef.get();
                        if (root == null) {
                            startSession(state, attached);
                        } else {
                            // Let the overlay attach finish before PassBlur samples the
                            // visible desktop, without toggling the Workspace output.
                            root.postOnAnimation(() -> startSession(state, attached));
                        }
                    }

                    @Override public void onAttachFailed(Throwable error) {
                        MainHook.log(TAG + " source overlay attach failed: " + error);
                        release(state, "source-attach-failed");
                    }
                }, "LiquidDockShortcutBackdropSource");
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
                        @Override public void onBackdropReady() {
                            // No Workspace presentation change is required for popup capture.
                        }

                        @Override public void onHwuiBackdropsReady(
                                android.graphics.Bitmap rawBitmap,
                                android.graphics.Bitmap blurredBitmap,
                                int width, int height) {
                            ShortcutPopupGlassCoordinator.onHwuiBackdropsReady(
                                    state, rawBitmap, blurredBitmap, width, height);
                        }

                        @Override public void onHwuiBackdropExportFailed(Throwable error) {
                            ShortcutPopupGlassCoordinator.onHwuiBackdropExportFailed(state, error);
                        }

                        @Override public void onMotionQualityChanged(boolean active) {
                            ShortcutPopupGlassCoordinator.onMotionQualityChanged(state, active);
                        }

                        @Override public void onPresented() {
                            ShortcutPopupGlassCoordinator.onPresented(state);
                        }

                        @Override public void onFailure(Throwable error) {
                            MainHook.log(TAG + " session failed: " + error);
                            release(state, "session-failure");
                        }
                    });
            // Stage the actual popup View and HWUI shaders during the hold.
            // Creating them only after ShortcutMenu.show() started the OEM
            // entrance was the remaining source of mid-animation handoff.
            if (BuildConfig.WORKSPACE_GLASS_HWUI_PROBE) {
                state.stagedLayer = new ShortcutPopupGlassLayer(
                        sourceRoot.getContext(), state.session);
            }
            state.session.requestInitialCapture();
            boolean outputReady = ensurePopupOutput(state);
            MainHook.log(TAG + " pre-show workspace capture requested popupReady=" + outputReady);
        }
    }

    private static synchronized void onHwuiBackdropsReady(
            State state, android.graphics.Bitmap rawBitmap,
            android.graphics.Bitmap blurredBitmap, int width, int height) {
        if (current != state || state.released) {
            if (rawBitmap != null) rawBitmap.recycle();
            if (blurredBitmap != null) blurredBitmap.recycle();
            return;
        }
        ShortcutPopupGlassLayer layer = state.layer != null
                ? state.layer : state.stagedLayer;
        if (layer == null) {
            if (state.pendingBackdropOwned) {
                if (state.pendingRawBackdrop != null) state.pendingRawBackdrop.recycle();
                if (state.pendingBlurredBackdrop != null) state.pendingBlurredBackdrop.recycle();
            }
            state.pendingRawBackdrop = rawBitmap;
            state.pendingBlurredBackdrop = blurredBitmap;
            state.pendingBackdropWidth = width;
            state.pendingBackdropHeight = height;
            state.pendingBackdropOwned = true;
            return;
        }
        if (!layer.setHwuiBackdrops(rawBitmap, blurredBitmap, width, height)) {
            if (rawBitmap != null) rawBitmap.recycle();
            if (blurredBitmap != null) blurredBitmap.recycle();
            layer.fallbackToTextureView();
        } else {
            MainHook.log(TAG + " popup HWUI prepared before/after bind="
                    + (state.layer != null) + " ageMs="
                    + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
            resumeDeferredShowWithGlass(state);
        }
    }

    private static synchronized void onHwuiBackdropExportFailed(State state, Throwable error) {
        if (current != state || state.released) return;
        MainHook.log(TAG + " popup HWUI backdrop export failed; restoring GL output: " + error);
        ShortcutPopupGlassLayer layer = state.layer != null
                ? state.layer : state.stagedLayer;
        if (layer != null) layer.fallbackToTextureView();
        if (state.pendingRawBackdrop != null) {
            if (state.pendingBackdropOwned) state.pendingRawBackdrop.recycle();
            state.pendingRawBackdrop = null;
        }
        if (state.pendingBlurredBackdrop != null) {
            if (state.pendingBackdropOwned) state.pendingBlurredBackdrop.recycle();
            state.pendingBlurredBackdrop = null;
        }
        state.pendingBackdropOwned = false;
    }

    private static synchronized void onMotionQualityChanged(State state, boolean active) {
        if (current != state || state.released) return;
        View root = state.captureRootRef.get();
        if (root != null) {
            LauncherGlassSessionRegistry.setPopupMotionQualityForRoot(root, active);
        }
        ShortcutPopupGlassLayer layer = state.layer != null
                ? state.layer : state.stagedLayer;
        if (layer != null) layer.setMotionQualityActive(active);
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
                ensurePopupOutput(state);
                updateGeometry(state);
                if (!state.firstPreDrawLogged) {
                    state.firstPreDrawLogged = true;
                    MainHook.log(TAG + " popup first preDraw ageMs="
                            + (SystemClock.uptimeMillis() - state.createdAtUptimeMs)
                            + " staged=" + (state.stagedLayer != null)
                            + " output=" + (state.layer != null)
                            + " hwuiDrawable=" + (state.layer != null
                                    && state.layer.hasPreparedHwuiFrame()));
                }
                // Claim the native material as soon as a complete HWUI frame can
                // draw. The first traversal may precede popup layout and texture
                // export; it must NEVER permanently disable the popup session.
                // onPresented() remains the fallback for GL output readiness.
                if (!state.materialClaimed && state.layer != null
                        && state.layer.hasPreparedHwuiFrame()) {
                    onPresented(state);
                }
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

        ShortcutPopupGlassLayer layer = state.stagedLayer != null
                ? state.stagedLayer : new ShortcutPopupGlassLayer(decor.getContext(), state.session);
        decorGroup.addView(layer, popupIndex, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        state.layer = layer;
        state.stagedLayer = null;
        if (state.pendingRawBackdrop != null && state.pendingBlurredBackdrop != null) {
            android.graphics.Bitmap raw = state.pendingRawBackdrop;
            android.graphics.Bitmap blurred = state.pendingBlurredBackdrop;
            state.pendingRawBackdrop = null;
            state.pendingBlurredBackdrop = null;
            if (!layer.setHwuiBackdrops(raw, blurred,
                    state.pendingBackdropWidth, state.pendingBackdropHeight)) {
                if (state.pendingBackdropOwned) {
                    raw.recycle();
                    blurred.recycle();
                }
                state.pendingBackdropOwned = false;
                layer.fallbackToTextureView();
            }
            state.pendingBackdropOwned = false;
        }
        updateGeometry(state);
        MainHook.log(TAG + " stable full-screen output inserted below PopupView index=" + popupIndex
                + " popupRoot=" + decor.getWidth() + "x" + decor.getHeight()
                + " backdropReady=" + state.session.hasFrozenBackdrop()
                + " hwuiPrepared=" + layer.hasPreparedHwuiFrame()
                + " ageMs=" + (SystemClock.uptimeMillis() - state.createdAtUptimeMs));
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
        float localRadius = GlassComponentCorners.resolve(
                state.glassConfig != null ? state.glassConfig.shortcutPopupCornerRadiusDp : -1f,
                resolveShortcutMenuCornerRadius(content), content.getResources().getDisplayMetrics().density,
                content.getWidth(), content.getHeight());
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolveStatic(
                decor.getWidth(), decor.getHeight(),
                left, top, right, bottom,
                localRadius * Math.max(0f, radiusScale));
        if (geometry != null) {
            session.updateGeometry(geometry);
            ShortcutPopupGlassLayer layer = state.layer;
            if (layer != null) layer.updatePopupGeometry(geometry);
        }
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
            MainHook.log(TAG + " workspace-backed popup glass presented; vendor material released"
                    + " ageMs=" + (SystemClock.uptimeMillis() - state.createdAtUptimeMs)
                    + " preDraw=" + state.firstPreDrawLogged);
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
        if (state.session != null) state.session.beginPopupDismiss();
        ShortcutPopupGlassLayer layer = state.layer;
        if (layer != null) {
            layer.fadeOutFast();
            MainHook.log(TAG + " fast dismiss fade started");
        }
    }

    static synchronized void cancelPending(View authorityOwner, View captureRoot) {
        State state = current;
        if (state == null || state.contentRef.get() != null
                || state.deferredShow != null) return;
        // The OEM may clear ItemInfo after invoking show(). A deferred show
        // must remain alive until glass is ready, timeout, or explicit dismiss.
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
        cancelScheduledWorkspacePrewarm(null);
        State state = current;
        current = null;
        if (state == null || state.released) return;
        state.released = true;
        cancelHandoffCleanup(state);
        Runnable pendingNativeShow = takeDeferredShow(state);
        boolean showNativeAfterFailure = "show-preparation-timeout".equals(reason)
                || "source-attach-failed".equals(reason)
                || "source-overlay-unavailable".equals(reason)
                || "session-failure".equals(reason)
                || "live-popup-glass-disabled".equals(reason);
        View captureRoot = state.captureRootRef.get();
        if (captureRoot != null) {
            LauncherGlassSessionRegistry.setPopupMotionQualityForRoot(captureRoot, false);
        }
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
        ShortcutPopupGlassLayer stagedLayer = state.stagedLayer;
        state.layer = null;
        state.stagedLayer = null;
        if (layer != null) layer.dispose();
        if (stagedLayer != null && stagedLayer != layer) stagedLayer.dispose();
        if (state.pendingBackdropOwned) {
            if (state.pendingRawBackdrop != null) state.pendingRawBackdrop.recycle();
            if (state.pendingBlurredBackdrop != null) state.pendingBlurredBackdrop.recycle();
        }
        state.pendingRawBackdrop = null;
        state.pendingBlurredBackdrop = null;
        state.pendingBackdropOwned = false;
        ShortcutPopupGlassSession session = state.session;
        state.session = null;
        ShortcutPopupSourceOverlay source = state.sourceOverlay;
        state.sourceOverlay = null;
        // Removing this WindowManager root immediately invalidates the SurfaceControl before
        // the async PassBlur detach Transaction can commit. Keep it attached until the backend
        // confirms native detach and releases EGL; a failure stays owned, never unhooks early.
        if (session != null) session.shutdown(source != null ? source::dispose : null);
        else if (source != null) source.dispose();
        if (showNativeAfterFailure && pendingNativeShow != null
                && captureRoot != null && captureRoot.isAttachedToWindow()) {
            captureRoot.post(pendingNativeShow);
        }
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
        LiquidDockConfig.Glass glassConfig;
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
        ShortcutPopupGlassLayer stagedLayer;
        final long createdAtUptimeMs = SystemClock.uptimeMillis();
        boolean firstPreDrawLogged;
        android.graphics.Bitmap pendingRawBackdrop;
        android.graphics.Bitmap pendingBlurredBackdrop;
        int pendingBackdropWidth;
        int pendingBackdropHeight;
        boolean pendingBackdropOwned;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        View.OnAttachStateChangeListener popupDetachListener;
        boolean requestStarted;
        boolean materialClaimed;
        boolean dismissCleanupPosted;
        Runnable handoffCleanup;
        Object deferredMenu;
        Runnable deferredShow;
        Runnable deferredShowTimeout;
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
