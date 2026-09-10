package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/** Coordinates one root-wide Security Center assistant / All Apps glass session. */
final class SecurityCenterGlassCoordinator
        implements SecurityCenterGlassRuntimeState.Owner, SecurityCenterGlassSession.Listener {
    static final class BindDecision {
        final boolean createSession;
        final boolean reuseSession;
        final Object sessionToShutdown;

        BindDecision(boolean createSession, boolean reuseSession, Object sessionToShutdown) {
            this.createSession = createSession;
            this.reuseSession = reuseSession;
            this.sessionToShutdown = sessionToShutdown;
        }
    }

    static final class ReleaseDecision {
        final Object sessionToShutdown;

        ReleaseDecision(Object sessionToShutdown) {
            this.sessionToShutdown = sessionToShutdown;
        }
    }

    /** Android-free identity authority used by queued callback and root replacement gates. */
    static final class Policy {
        private Object currentRoot;
        private Object currentSession;

        synchronized BindDecision bindRoot(Object root) {
            if (root == null) return new BindDecision(false, false, null);
            if (root == currentRoot) {
                return currentSession != null
                        ? new BindDecision(false, true, null)
                        : new BindDecision(true, false, null);
            }
            Object old = currentSession;
            currentRoot = root;
            currentSession = null;
            return new BindDecision(true, false, old);
        }

        synchronized boolean onSessionCreated(Object root, Object session) {
            if (root == null || session == null || root != currentRoot || currentSession != null) {
                return false;
            }
            currentSession = session;
            return true;
        }

        synchronized boolean acceptsCallback(
                Object root,
                Object session,
                long renderedGeneration,
                long currentGeneration,
                boolean runtimeEnabled) {
            return runtimeEnabled
                    && root != null
                    && session != null
                    && root == currentRoot
                    && session == currentSession
                    && renderedGeneration >= 0L
                    && renderedGeneration == currentGeneration;
        }

        synchronized ReleaseDecision releaseAll() {
            Object old = currentSession;
            currentRoot = null;
            currentSession = null;
            return new ReleaseDecision(old);
        }

        synchronized Object currentRoot() { return currentRoot; }
        synchronized Object currentSession() { return currentSession; }
    }

    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    private final Policy policy = new Policy();
    private final SecurityCenterGlassSceneState scene = new SecurityCenterGlassSceneState();
    private final SecurityCenterMaterialOwnershipState ownership =
            new SecurityCenterMaterialOwnershipState();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LiquidDockConfig.Glass glassConfig;
    private final SecurityCenterVendorMaterialBridge vendorMaterialBridge;
    private final int allAppsCornerRadiusResId;
    private final int gameToolboxCornerRadiusResId;

    private WeakReference<View> turboRef = new WeakReference<>(null);
    private WeakReference<View> dockRef = new WeakReference<>(null);
    private WeakReference<View> boxRef = new WeakReference<>(null);
    private WeakReference<View> appsRef = new WeakReference<>(null);
    private WeakReference<View> rootRef = new WeakReference<>(null);
    private WeakReference<View> customOwnerTurboRef = new WeakReference<>(null);
    private SecurityCenterGlassSceneState.Target targetKind =
            SecurityCenterGlassSceneState.Target.DOCK;
    private int assistantType = ASSISTANT_GLOBAL_DOCK;
    private SecurityCenterGlassFrameGeometry currentFrame;
    private SecurityCenterGlassSession session;
    private SecurityCenterGlassOutputView output;
    private long renderedGeneration = -1L;
    private ViewTreeObserver rootObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private View attachObservedTurbo;
    private View attachedRoot;

    private final View.OnAttachStateChangeListener turboAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    if (v == turboRef.get()) bindAttachedRoot(v);
                }

                @Override public void onViewDetachedFromWindow(View v) {
                    // Fallback only. Authoritative close is ob.e0.d2/f2 and revokes before teardown.
                    mainHandler.post(() -> {
                        if (v == turboRef.get() && !v.isAttachedToWindow()) {
                            releasePanel(v, "panel detached fallback");
                        }
                    });
                }
            };

    private final View.OnAttachStateChangeListener rootAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}

                @Override public void onViewDetachedFromWindow(View v) {
                    mainHandler.post(() -> {
                        if (policy.currentRoot() == v && rootRef.get() == v
                                && !v.isAttachedToWindow()) {
                            releaseForRootDetach();
                        }
                    });
                }
            };

    SecurityCenterGlassCoordinator(LiquidDockConfig.Glass glassConfig) {
        this(glassConfig, null, 0, 0);
    }

    SecurityCenterGlassCoordinator(
            LiquidDockConfig.Glass glassConfig,
            SecurityCenterVendorMaterialBridge vendorMaterialBridge) {
        this(glassConfig, vendorMaterialBridge, 0, 0);
    }

    SecurityCenterGlassCoordinator(
            LiquidDockConfig.Glass glassConfig,
            SecurityCenterVendorMaterialBridge vendorMaterialBridge,
            int allAppsCornerRadiusResId) {
        this(glassConfig, vendorMaterialBridge, allAppsCornerRadiusResId, 0);
    }

    SecurityCenterGlassCoordinator(
            LiquidDockConfig.Glass glassConfig,
            SecurityCenterVendorMaterialBridge vendorMaterialBridge,
            int allAppsCornerRadiusResId,
            int gameToolboxCornerRadiusResId) {
        this.glassConfig = glassConfig;
        this.vendorMaterialBridge = vendorMaterialBridge;
        this.allAppsCornerRadiusResId = allAppsCornerRadiusResId;
        this.gameToolboxCornerRadiusResId = gameToolboxCornerRadiusResId;
        SecurityCenterGlassRuntimeState.setOwner(this);
    }

    /** Compatibility entry used by the existing Global Dock lifecycle. */
    void bindGlobalDock(View turboLayout, View dockLayout) {
        bindAssistant(turboLayout, dockLayout, null, ASSISTANT_GLOBAL_DOCK);
    }

    /** Binds one vendor c0() result; Dock, Game, Video and Global Dock share one root session. */
    void bindAssistant(View turboLayout, View dockLayout, View boxLayout, int type) {
        if (turboLayout == null || dockLayout == null || !supportedAssistant(type)) return;
        View previousTurbo = turboRef.get();
        if (previousTurbo != null && previousTurbo != turboLayout) {
            releasePanel(previousTurbo, "panel replaced");
        }
        boolean replacingLiveNodes = previousTurbo == turboLayout
                && ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM;
        turboRef = new WeakReference<>(turboLayout);
        dockRef = new WeakReference<>(dockLayout);
        boxRef = new WeakReference<>(boxLayout);
        appsRef = new WeakReference<>(null);
        assistantType = type;
        targetKind = SecurityCenterGlassSceneState.Target.DOCK;
        currentFrame = null;
        observeTurboAttach(turboLayout);
        if (!SecurityCenterGlassRuntimeState.isEnabled()) {
            releaseAll();
            return;
        }

        // c0() has synchronously created the replacement vendor nodes. If this root is already
        // under custom ownership, strip those new vendor materials before Android can draw them;
        // the previous valid custom frame remains the presentation fallback until a fresh render.
        if (replacingLiveNodes) claimCurrentTargetsWhileCustom();
        if (turboLayout.isAttachedToWindow()) bindAttachedRoot(turboLayout);
        log("assistant bound type=" + type
                + " box=" + (boxLayout != null ? boxLayout.getClass().getName() : "none"), null);
    }

    void updateAllAppsLayout(View turboLayout, View appsLayout) {
        if (!isCurrentTurbo(turboLayout) || appsLayout == null) return;
        appsRef = new WeakReference<>(appsLayout);
        // W() creates All Apps synchronously inside d0(). If custom already owns this lifecycle,
        // remove the newly-created native material immediately instead of waiting for settle.
        if (ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM) {
            claimCurrentTargetsWhileCustom();
        }
    }

    long onAllAppsToggleStarted(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return -1L;
        SecurityCenterGlassSceneState.Decision decision = scene.onTransitionStarted();
        applyDecision(decision, null);
        return decision.invalidateGeneration ? decision.generation : -1L;
    }

    /** Called on every vendor-transform pre-draw while raw DEX field s remains true. */
    void refreshTransitionFrame(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()
                || scene.scene() != SecurityCenterGlassSceneState.Scene.TRANSITIONING) return;
        SecurityCenterGlassFrameGeometry observed = captureFrame(true);
        if (observed == null) return;
        boolean changed = currentFrame == null || !currentFrame.sameAs(observed);
        currentFrame = observed;
        updateOutputPlacement(observed);
        if (!changed) return;
        requestCurrentGeneration(observed);
    }

    void onAllAppsToggleSettled(
            View turboLayout, boolean allAppsPresent, long transitionGeneration) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return;
        SecurityCenterGlassSceneState.Target nextKind = allAppsPresent
                ? SecurityCenterGlassSceneState.Target.ALL_APPS
                : SecurityCenterGlassSceneState.Target.DOCK;
        SecurityCenterGlassFrameGeometry frame = captureFrame(allAppsPresent);
        if (frame == null) {
            log("settled frame unavailable target=" + nextKind
                    + " generation=" + transitionGeneration, null);
            return;
        }
        SecurityCenterGlassSceneState.Decision settled =
                scene.onGeometrySettled(nextKind, transitionGeneration);
        if (!settled.requestFresh) {
            log("stale settle ignored target=" + nextKind
                    + " generation=" + transitionGeneration
                    + " currentGeneration=" + scene.generation(), null);
            return;
        }
        targetKind = nextKind;
        currentFrame = frame;
        updateOutputPlacement(frame);
        applyDecision(settled, frame);
    }

    /** Called before vendor d2/f2 mutates or removes the panel. */
    void onVendorPanelClosing(View turboLayout) {
        releasePanel(turboLayout, "vendor panel close");
    }

    boolean shouldSuppressVendorFinalBackground(Object turboLayout) {
        return turboLayout != null
                && turboLayout == turboRef.get()
                && ownership.canSuppressVendor(renderedGeneration, scene.generation())
                && policy.currentSession() == session
                && SecurityCenterGlassRuntimeState.isEnabled();
    }

    @Override
    public void releaseAll() {
        View turbo = turboRef.get();
        if (turbo != null) {
            releasePanel(turbo, "runtime release");
            return;
        }
        ReleaseDecision release = policy.releaseAll();
        if (scene.scene() != SecurityCenterGlassSceneState.Scene.DETACHED) {
            scene.onRuntimeDisabled();
        }
        hideAndRestoreVendor();
        cleanupViewObservers(true);
        SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
        session = null;
        if (old != null) old.shutdown();
        clearFrameState();
    }

    @Override
    public void onFrameRendered(SecurityCenterGlassSession callbackSession, long generation) {
        View root = rootRef.get();
        if (!policy.acceptsCallback(
                root, callbackSession, generation, scene.generation(),
                SecurityCenterGlassRuntimeState.isEnabled())) return;
        if (callbackSession != session || callbackSession.isShutdown()
                || root == null || !root.isAttachedToWindow()) return;

        SecurityCenterGlassSceneState.Decision decision = scene.onFreshFrameRendered(generation);
        if (!decision.claimCustomOwnership || !decision.revealCustom) return;
        View turbo = turboRef.get();
        View dock = dockRef.get();
        View box = currentFrame != null && currentFrame.boxGeometry() != null ? boxRef.get() : null;
        View apps = currentFrame != null && currentFrame.appsGeometry() != null ? appsRef.get() : null;
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        if (turbo == null || dock == null || bridge == null) return;
        try {
            bridge.claimCustom(turbo, dock, box, apps);
        } catch (Throwable error) {
            hideCustomOnly();
            ownership.releaseToVendor();
            renderedGeneration = -1L;
            try { bridge.restoreVendor(turbo); }
            catch (Throwable restoreError) { log("claim rollback restore failed", restoreError); }
            log("custom material claim failed closed", error);
            return;
        }

        ownership.onCustomClaimed();
        customOwnerTurboRef = new WeakReference<>(turbo);
        renderedGeneration = generation;
        SecurityCenterGlassOutputView current = output;
        if (current != null && !current.isDisposed()) current.setAuthorizedVisible(true);
        log("current-generation scene revealed generation=" + generation
                + " scene=" + scene.scene()
                + " nodes=" + (currentFrame != null ? currentFrame.nodeCount() : 0), null);
    }

    @Override
    public void onTerminalFailure(
            SecurityCenterGlassSession callbackSession, long generation, Throwable error) {
        View root = rootRef.get();
        if (!policy.acceptsCallback(
                root, callbackSession, generation, scene.generation(),
                SecurityCenterGlassRuntimeState.isEnabled())) return;
        if (callbackSession != session || callbackSession.isShutdown()
                || root == null || !root.isAttachedToWindow()) return;
        scene.onTerminalFailure();
        View turbo = turboRef.get();
        if (turbo != null) releasePanel(turbo, "terminal failure");
        else releaseAll();
        log("failed closed generation=" + generation, error);
    }

    private void bindAttachedRoot(View turboLayout) {
        if (!SecurityCenterGlassRuntimeState.isEnabled() || !isCurrentTurbo(turboLayout)
                || !turboLayout.isAttachedToWindow()
                || !(turboLayout.getParent() instanceof ViewGroup)) return;
        View root = turboLayout.getRootView();
        if (root == null || !root.isAttachedToWindow()) return;

        BindDecision bind = policy.bindRoot(root);
        if (bind.reuseSession) {
            ensureOutputSibling(turboLayout);
            installRootObserver(root);
            refreshCurrentFrame(true);
            return;
        }
        if (!bind.createSession) return;

        SecurityCenterGlassSession old = bind.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) bind.sessionToShutdown : session;
        hideAndRestoreVendor();
        if (old != null) old.shutdown();
        cleanupRootObserverOnly();
        if (scene.scene() != SecurityCenterGlassSceneState.Scene.DETACHED) {
            scene.onRootDetached();
        }

        session = new SecurityCenterGlassSession(root, glassConfig, this);
        if (!policy.onSessionCreated(root, session)) {
            session.shutdown();
            session = null;
            return;
        }
        rootRef = new WeakReference<>(root);
        attachRootListener(root);
        ensureOutputSibling(turboLayout);
        installRootObserver(root);

        SecurityCenterGlassSceneState.Decision attached = scene.onRootAttached();
        applyDecision(attached, null);
        SecurityCenterGlassFrameGeometry frame = captureFrame(false);
        if (frame != null) {
            currentFrame = frame;
            updateOutputPlacement(frame);
        }
        applyDecision(
                scene.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK), frame);
    }

    private void ensureOutputSibling(View turboLayout) {
        SecurityCenterGlassSession live = session;
        if (live == null || live.isShutdown() || turboLayout == null
                || !(turboLayout.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) turboLayout.getParent();
        SecurityCenterGlassOutputView current = output;
        if (current != null && !current.isDisposed() && current.getParent() == parent
                && parent.indexOfChild(current) == parent.indexOfChild(turboLayout) - 1) {
            if (currentFrame != null) current.updatePlacement(
                    rootRef.get(), currentFrame.presentationGeometry());
            return;
        }
        if (current != null) current.dispose();
        output = SecurityCenterGlassOutputView.attachBefore(turboLayout, live);
        if (output != null && currentFrame != null) {
            output.updatePlacement(rootRef.get(), currentFrame.presentationGeometry());
        }
    }

    private void updateOutputPlacement(SecurityCenterGlassFrameGeometry frame) {
        SecurityCenterGlassOutputView current = output;
        View root = rootRef.get();
        if (current != null && !current.isDisposed() && root != null && frame != null) {
            current.updatePlacement(root, frame.presentationGeometry());
        }
    }

    private void applyDecision(
            SecurityCenterGlassSceneState.Decision decision,
            SecurityCenterGlassFrameGeometry frame) {
        if (decision == null) return;
        if (decision.hideCustom || decision.releaseCustomOwnership) hideAndRestoreVendor();
        if (frame != null) updateOutputPlacement(frame);
        if (decision.requestFresh && frame != null && decision.generation == scene.generation()) {
            requestCurrentGeneration(frame);
        }
        if (decision.shutdownSession) {
            ReleaseDecision release = policy.releaseAll();
            SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                    ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
            session = null;
            if (old != null) old.shutdown();
        }
    }

    private void requestCurrentGeneration(SecurityCenterGlassFrameGeometry frame) {
        SecurityCenterGlassSession live = session;
        View root = rootRef.get();
        long generation = scene.generation();
        if (live != null && !live.isShutdown() && root != null
                && policy.currentSession() == live && policy.currentRoot() == root
                && frame != null) {
            live.requestFresh(generation, frame);
        }
    }

    private void refreshCurrentFrame(boolean requestIfChanged) {
        if (!SecurityCenterGlassRuntimeState.isEnabled()
                || scene.scene() == SecurityCenterGlassSceneState.Scene.DETACHED) return;
        if (scene.scene() == SecurityCenterGlassSceneState.Scene.TRANSITIONING) {
            refreshTransitionFrame(turboRef.get());
            return;
        }
        boolean includeApps = targetKind == SecurityCenterGlassSceneState.Target.ALL_APPS;
        SecurityCenterGlassFrameGeometry observed = captureFrame(includeApps);
        if (observed == null) return;
        boolean changed = currentFrame == null || !currentFrame.sameAs(observed);
        currentFrame = observed;
        updateOutputPlacement(observed);
        if (requestIfChanged && changed) requestCurrentGeneration(observed);
    }

    private SecurityCenterGlassFrameGeometry captureFrame(boolean includeAppsIfAvailable) {
        SecurityCenterGlassGeometry dock = captureGeometry(
                dockRef.get(), SecurityCenterGlassSceneState.Target.DOCK, 0);
        if (dock == null) return null;
        SecurityCenterGlassGeometry box = captureBoxGeometry();
        SecurityCenterGlassGeometry apps = null;
        if (includeAppsIfAvailable) {
            View appsView = appsRef.get();
            if (appsView != null && appsView.isAttachedToWindow()
                    && appsView.getVisibility() == View.VISIBLE) {
                apps = captureGeometry(
                        appsView, SecurityCenterGlassSceneState.Target.ALL_APPS,
                        allAppsCornerRadiusResId);
                if (apps == null) return null;
            }
        }
        try {
            return SecurityCenterGlassFrameGeometry.compose(dock, box, apps);
        } catch (IllegalArgumentException error) {
            log("frame composition rejected", error);
            return null;
        }
    }

    private SecurityCenterGlassGeometry captureBoxGeometry() {
        View box = boxRef.get();
        if (box == null || !box.isAttachedToWindow() || box.getVisibility() != View.VISIBLE) return null;
        int radiusRes = assistantType == ASSISTANT_GAME ? gameToolboxCornerRadiusResId : 0;
        return captureGeometry(box, null, radiusRes);
    }

    private SecurityCenterGlassGeometry captureGeometry(
            View target,
            SecurityCenterGlassSceneState.Target kind,
            int exactRadiusResId) {
        View root = rootRef.get();
        if (root == null || target == null || !root.isAttachedToWindow()
                || !target.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;

        ViewParent rawParent = target.getParent();
        if (!(rawParent instanceof View)) return null;
        View parent = (View) rawParent;
        int[] rootLocation = new int[2];
        int[] parentLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        parent.getLocationOnScreen(parentLocation);

        float baseLeft = parentLocation[0] + target.getLeft() - parent.getScrollX();
        float baseTop = parentLocation[1] + target.getTop() - parent.getScrollY();
        SecurityCenterTransformedBounds bounds = SecurityCenterTransformedBounds.resolve(
                baseLeft,
                baseTop,
                target.getWidth(),
                target.getHeight(),
                target.getPivotX(),
                target.getPivotY(),
                target.getScaleX(),
                target.getScaleY(),
                target.getTranslationX(),
                target.getTranslationY());
        if (bounds == null) return null;

        float radius;
        if (exactRadiusResId != 0) {
            radius = target.getResources().getDimension(exactRadiusResId);
            if (!Float.isFinite(radius) || radius < 0f) return null;
            float visualScale = Math.min(target.getScaleX(), target.getScaleY());
            if (!Float.isFinite(visualScale) || visualScale <= 0f) return null;
            radius *= visualScale;
        } else if (kind == SecurityCenterGlassSceneState.Target.ALL_APPS) {
            return null;
        } else {
            radius = MiuixGlassHook.readNativeOpticsRadius(target);
            if (!Float.isFinite(radius) || radius < 0f) radius = 0f;
            float visualScale = Math.min(target.getScaleX(), target.getScaleY());
            if (Float.isFinite(visualScale) && visualScale > 0f) radius *= visualScale;
        }

        return SecurityCenterGlassGeometry.resolve(
                root.getWidth(), root.getHeight(),
                rootLocation[0], rootLocation[1],
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                radius);
    }

    private void claimCurrentTargetsWhileCustom() {
        if (ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.CUSTOM) return;
        View turbo = turboRef.get();
        View dock = dockRef.get();
        View box = boxRef.get();
        View apps = appsRef.get();
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        if (turbo == null || dock == null || bridge == null) return;
        try {
            bridge.claimCustom(turbo, dock, box, apps);
        } catch (Throwable error) {
            log("live material continuity claim failed", error);
        }
    }

    private void installRootObserver(View root) {
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer == rootObserver && preDrawListener != null && observer.isAlive()) return;
        cleanupRootObserverOnly();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            View liveRoot = rootRef.get();
            SecurityCenterGlassSession liveSession = session;
            if (SecurityCenterGlassRuntimeState.isEnabled()
                    && liveRoot == root
                    && policy.currentRoot() == root
                    && policy.currentSession() == liveSession
                    && liveSession != null && !liveSession.isShutdown()) {
                refreshCurrentFrame(true);
            }
            return true;
        };
        observer.addOnPreDrawListener(listener);
        rootObserver = observer;
        preDrawListener = listener;
    }

    private void attachRootListener(View root) {
        if (attachedRoot == root) return;
        if (attachedRoot != null) {
            try { attachedRoot.removeOnAttachStateChangeListener(rootAttachListener); }
            catch (Throwable ignored) {}
        }
        attachedRoot = root;
        root.addOnAttachStateChangeListener(rootAttachListener);
    }

    private void observeTurboAttach(View turboLayout) {
        if (attachObservedTurbo == turboLayout) return;
        if (attachObservedTurbo != null) {
            try { attachObservedTurbo.removeOnAttachStateChangeListener(turboAttachListener); }
            catch (Throwable ignored) {}
        }
        attachObservedTurbo = turboLayout;
        turboLayout.addOnAttachStateChangeListener(turboAttachListener);
    }

    private boolean isCurrentTurbo(View turboLayout) {
        return turboLayout != null && turboLayout == turboRef.get();
    }

    private static boolean supportedAssistant(int type) {
        return type == ASSISTANT_GAME || type == ASSISTANT_VIDEO || type == ASSISTANT_GLOBAL_DOCK;
    }

    private void hideCustomOnly() {
        SecurityCenterGlassOutputView current = output;
        if (current != null && !current.isDisposed()) current.setAuthorizedVisible(false);
    }

    private void hideAndRestoreVendor() {
        hideCustomOnly();
        boolean wasCustom = ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM;
        View ownedTurbo = customOwnerTurboRef.get();
        ownership.releaseToVendor();
        renderedGeneration = -1L;
        customOwnerTurboRef = new WeakReference<>(null);
        if (!wasCustom || ownedTurbo == null || vendorMaterialBridge == null) return;
        try {
            vendorMaterialBridge.restoreVendor(ownedTurbo);
        } catch (Throwable error) {
            log("vendor material restore failed", error);
        }
    }

    private void releasePanel(View turboLayout, String reason) {
        if (turboLayout == null || turboLayout != turboRef.get()) return;
        ReleaseDecision release = policy.releaseAll();
        if (scene.scene() != SecurityCenterGlassSceneState.Scene.DETACHED) scene.onRootDetached();
        hideAndRestoreVendor();
        cleanupViewObservers(true);
        SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
        session = null;
        if (old != null) old.shutdown();
        clearFrameState();
        log(reason + "; custom glass released", null);
    }

    private void releaseForRootDetach() {
        ReleaseDecision release = policy.releaseAll();
        if (scene.scene() != SecurityCenterGlassSceneState.Scene.DETACHED) scene.onRootDetached();
        hideAndRestoreVendor();
        cleanupViewObservers(true);
        SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
        session = null;
        if (old != null) old.shutdown();
        clearFrameState();
        log("root detached; custom glass released", null);
    }

    private void clearFrameState() {
        rootRef = new WeakReference<>(null);
        currentFrame = null;
        targetKind = SecurityCenterGlassSceneState.Target.DOCK;
        assistantType = ASSISTANT_GLOBAL_DOCK;
        renderedGeneration = -1L;
    }

    private void cleanupViewObservers(boolean removeTurboListener) {
        cleanupRootObserverOnly();
        if (attachedRoot != null) {
            try { attachedRoot.removeOnAttachStateChangeListener(rootAttachListener); }
            catch (Throwable ignored) {}
            attachedRoot = null;
        }
        SecurityCenterGlassOutputView current = output;
        output = null;
        if (current != null) current.dispose();
        if (removeTurboListener && attachObservedTurbo != null) {
            try { attachObservedTurbo.removeOnAttachStateChangeListener(turboAttachListener); }
            catch (Throwable ignored) {}
            attachObservedTurbo = null;
            turboRef = new WeakReference<>(null);
            dockRef = new WeakReference<>(null);
            boxRef = new WeakReference<>(null);
            appsRef = new WeakReference<>(null);
        }
    }

    private void cleanupRootObserverOnly() {
        ViewTreeObserver observer = rootObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        rootObserver = null;
        preDrawListener = null;
        if (observer != null && listener != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(listener); }
            catch (Throwable ignored) {}
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
