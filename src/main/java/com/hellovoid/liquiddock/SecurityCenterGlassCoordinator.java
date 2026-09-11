package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
    private SecurityCenterGlassSinkView dockSink;
    private SecurityCenterGlassSinkView boxSink;
    private SecurityCenterGlassSinkView appsSink;
    private long renderedGeneration = -1L;
    private long requestedGeneration = -1L;
    private long vendorClearCommitEpoch;
    private boolean vendorClearCommitPending;
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
                    // Fallback only. The semantically resolved manager teardown hooks revoke first.
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
                && ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR;
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

        if (turboLayout.isAttachedToWindow()) bindAttachedRoot(turboLayout);
        if (replacingLiveNodes) {
            reconcileSinks();
            claimCurrentTargetsWhileCustom();
            syncSinksFromMaterials();
        }
        log("assistant bound type=" + type
                + " box=" + (boxLayout != null ? boxLayout.getClass().getName() : "none"), null);
    }

    void updateAllAppsLayout(View turboLayout, View appsLayout) {
        if (!isCurrentTurbo(turboLayout) || appsLayout == null) return;
        appsRef = new WeakReference<>(appsLayout);
        reconcileSinks();
        if (ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR) {
            claimCurrentTargetsWhileCustom();
        }
        syncSinksFromMaterials();
    }

    long onAllAppsToggleStarted(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return -1L;
        SecurityCenterGlassSceneState.Decision decision = scene.onTransitionStarted();
        applyDecision(decision, null);
        return decision.invalidateGeneration ? decision.generation : -1L;
    }

    void refreshTransitionFrame(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()
                || scene.scene() != SecurityCenterGlassSceneState.Scene.TRANSITIONING) return;
        reconcileSinks();
        syncSinksFromMaterials();
        SecurityCenterGlassFrameGeometry observed = captureFrame(true);
        if (observed == null) return;
        boolean changed = currentFrame == null || !currentFrame.sameAs(observed);
        currentFrame = observed;
        if (!changed) return;
        requestCurrentGeneration(observed);
    }

    void onAllAppsToggleSettled(
            View turboLayout, boolean allAppsPresent, long transitionGeneration) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return;
        SecurityCenterGlassSceneState.Target nextKind = allAppsPresent
                ? SecurityCenterGlassSceneState.Target.ALL_APPS
                : SecurityCenterGlassSceneState.Target.DOCK;
        reconcileSinks();
        syncSinksFromMaterials();
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
        applyDecision(settled, frame);
    }

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

        if (scene.scene() == SecurityCenterGlassSceneState.Scene.TRANSITIONING) {
            if (ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.VENDOR) return;
            ownership.onCustomPresented();
            renderedGeneration = generation;
            setAuthorizedSinksForFrame(currentFrame);
            log("current-generation transition frame revealed generation=" + generation
                    + " nodes=" + (currentFrame != null ? currentFrame.nodeCount() : 0), null);
            return;
        }

        SecurityCenterGlassSceneState.Decision decision = scene.onFreshFrameRendered(generation);
        if (!decision.revealCustom
                || ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.VENDOR) return;
        ownership.onCustomPresented();
        renderedGeneration = generation;
        setAuthorizedSinksForFrame(currentFrame);
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
            reconcileSinks();
            installRootObserver(root);
            refreshCurrentFrame(true);
            return;
        }
        if (!bind.createSession) return;

        SecurityCenterGlassSession old = bind.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) bind.sessionToShutdown : session;
        hideAndRestoreVendor();
        disposeSinks();
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
        reconcileSinks();
        installRootObserver(root);

        SecurityCenterGlassSceneState.Decision attached = scene.onRootAttached();
        applyDecision(attached, null);
        SecurityCenterGlassFrameGeometry frame = captureFrame(false);
        if (frame != null) {
            currentFrame = frame;
        }
        applyDecision(
                scene.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK), frame);
    }

    private void reconcileSinks() {
        SecurityCenterGlassSession live = session;
        if (live == null || live.isShutdown()) return;
        dockSink = reconcileSink(
                dockSink, dockRef.get(), live, SecurityCenterGlassSceneState.Target.DOCK, 0);
        int boxRadius = assistantType == ASSISTANT_GAME
                ? gameToolboxCornerRadiusResId
                : assistantType == ASSISTANT_VIDEO ? allAppsCornerRadiusResId : 0;
        boxSink = reconcileSink(boxSink, boxRef.get(), live, null, boxRadius);
        appsSink = reconcileSink(
                appsSink, appsRef.get(), live,
                SecurityCenterGlassSceneState.Target.ALL_APPS, allAppsCornerRadiusResId);
    }

    private SecurityCenterGlassSinkView reconcileSink(
            SecurityCenterGlassSinkView current,
            View material,
            SecurityCenterGlassSession live,
            SecurityCenterGlassSceneState.Target kind,
            int exactRadiusResId) {
        if (material == null || !(material.getParent() instanceof ViewGroup)
                || !material.isAttachedToWindow()) {
            if (current != null) current.dispose();
            return null;
        }
        if (current != null && current.ownsMaterial(material)
                && current.getParent() == material.getParent()) {
            current.syncFromMaterial();
            return current;
        }
        if (current != null) current.dispose();
        float radius = resolveBaseCornerRadius(material, kind, exactRadiusResId);
        if (!Float.isFinite(radius) || radius < 0f) return null;
        return SecurityCenterGlassSinkView.attachBefore(material, live, radius);
    }

    private float resolveBaseCornerRadius(
            View target, SecurityCenterGlassSceneState.Target kind, int exactRadiusResId) {
        if (target == null) return Float.NaN;
        if (exactRadiusResId != 0) {
            float radius = target.getResources().getDimension(exactRadiusResId);
            return Float.isFinite(radius) && radius >= 0f ? radius : Float.NaN;
        }
        if (kind == SecurityCenterGlassSceneState.Target.ALL_APPS) return Float.NaN;
        float radius = MiuixGlassHook.readNativeOpticsRadius(target);
        return Float.isFinite(radius) && radius >= 0f ? radius : 0f;
    }

    private boolean syncSinksFromMaterials() {
        boolean changed = false;
        if (dockSink != null) changed |= dockSink.syncFromMaterial();
        if (boxSink != null) changed |= boxSink.syncFromMaterial();
        if (appsSink != null) changed |= appsSink.syncFromMaterial();
        return changed;
    }

    private void setAuthorizedSinksForFrame(SecurityCenterGlassFrameGeometry frame) {
        boolean hasFrame = frame != null;
        if (dockSink != null) dockSink.setAuthorizedVisible(hasFrame);
        if (boxSink != null) {
            boxSink.setAuthorizedVisible(hasFrame && frame.boxGeometry() != null);
        }
        if (appsSink != null) {
            appsSink.setAuthorizedVisible(hasFrame && frame.appsGeometry() != null);
        }
    }

    private void applyDecision(
            SecurityCenterGlassSceneState.Decision decision,
            SecurityCenterGlassFrameGeometry frame) {
        if (decision == null) return;
        if (decision.hideCustom || decision.releaseCustomOwnership) hideAndRestoreVendor();

        boolean claimScheduledCommit = false;
        if (decision.claimCustomOwnership && decision.generation == scene.generation()) {
            claimScheduledCommit = prepareCustomOwnershipForCleanFrame();
        }
        if (decision.requestFresh && frame != null && decision.generation == scene.generation()
                && !claimScheduledCommit) {
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

    private boolean prepareCustomOwnershipForCleanFrame() {
        if (ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR) {
            return vendorClearCommitPending;
        }
        View turbo = turboRef.get();
        View dock = dockRef.get();
        View box = currentFrame != null && currentFrame.boxGeometry() != null ? boxRef.get() : null;
        View apps = currentFrame != null && currentFrame.appsGeometry() != null ? appsRef.get() : null;
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        if (turbo == null || dock == null || bridge == null) return false;
        try {
            hideCustomOnly();
            bridge.claimCustom(turbo, dock, box, apps);
            ownership.onCustomPreparing();
            customOwnerTurboRef = new WeakReference<>(turbo);
            renderedGeneration = -1L;
            requestedGeneration = -1L;
            if (!scheduleVendorClearFrameCommit()) {
                throw new IllegalStateException("vendor-clear frame commit unavailable");
            }
            log("vendor material cleared; waiting for UI frame commit generation="
                    + scene.generation(), null);
            return true;
        } catch (Throwable error) {
            ownership.releaseToVendor();
            customOwnerTurboRef = new WeakReference<>(null);
            renderedGeneration = -1L;
            try { bridge.restoreVendor(turbo); }
            catch (Throwable restoreError) { log("preclaim rollback restore failed", restoreError); }
            log("custom preclaim failed closed", error);
            return true;
        }
    }

    private boolean scheduleVendorClearFrameCommit() {
        if (vendorClearCommitPending) return true;
        View root = rootRef.get();
        SecurityCenterGlassSession expectedSession = session;
        if (root == null || expectedSession == null || expectedSession.isShutdown()
                || !root.isAttachedToWindow() || !root.isHardwareAccelerated()) return false;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return false;

        final long epoch = ++vendorClearCommitEpoch;
        vendorClearCommitPending = true;
        try {
            observer.registerFrameCommitCallback(() -> mainHandler.post(() -> {
                if (epoch != vendorClearCommitEpoch || !vendorClearCommitPending
                        || root != rootRef.get() || expectedSession != session
                        || policy.currentRoot() != root
                        || policy.currentSession() != expectedSession
                        || expectedSession.isShutdown()
                        || ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.VENDOR) {
                    return;
                }
                vendorClearCommitPending = false;
                SecurityCenterGlassFrameGeometry frame = currentFrame;
                if (frame == null) {
                    reconcileSinks();
                    syncSinksFromMaterials();
                    frame = captureFrame(targetKind == SecurityCenterGlassSceneState.Target.ALL_APPS);
                    currentFrame = frame;
                }
                if (frame == null) {
                    if (awaitingPresentationReadiness()) {
                        log("vendor-clear committed; waiting for compositable targets generation="
                                + scene.generation(), null);
                        root.postInvalidateOnAnimation();
                        return;
                    }
                    releasePanel(turboRef.get(), "clean-frame geometry unavailable");
                    return;
                }
                log("vendor-clear UI frame committed; requesting clean source generation="
                        + scene.generation(), null);
                requestCurrentGeneration(frame);
            }));
            root.postInvalidateOnAnimation();
            return true;
        } catch (Throwable error) {
            vendorClearCommitPending = false;
            vendorClearCommitEpoch++;
            log("vendor-clear frame commit registration failed", error);
            return false;
        }
    }

    private void requestCurrentGeneration(SecurityCenterGlassFrameGeometry frame) {
        SecurityCenterGlassSession live = session;
        View root = rootRef.get();
        long generation = scene.generation();
        if (vendorClearCommitPending) return;
        if (live == null || live.isShutdown() || root == null
                || policy.currentSession() != live || policy.currentRoot() != root
                || frame == null || dockSink == null || !dockSink.isPresentationReady()) return;

        int count = frame.nodeCount();
        SecurityCenterGlassSinkView[] sinks = new SecurityCenterGlassSinkView[count];
        int cursor = 0;
        sinks[cursor++] = dockSink;
        if (frame.boxGeometry() != null) {
            if (boxSink == null || !boxSink.isPresentationReady()) return;
            sinks[cursor++] = boxSink;
        }
        if (frame.appsGeometry() != null) {
            if (appsSink == null || !appsSink.isPresentationReady()) return;
            sinks[cursor++] = appsSink;
        }
        if (cursor != count) return;
        live.requestFresh(generation, frame, sinks);
        requestedGeneration = generation;
    }

    private boolean awaitingPresentationReadiness() {
        View dock = dockRef.get();
        if (dock != null && dock.isAttachedToWindow() && dockSink != null
                && !dockSink.isPresentationReady()) return true;
        View box = boxRef.get();
        if (box != null && box.isAttachedToWindow() && box.getVisibility() == View.VISIBLE
                && boxSink != null && !boxSink.isPresentationReady()) return true;
        View apps = appsRef.get();
        return apps != null && apps.isAttachedToWindow()
                && apps.getVisibility() == View.VISIBLE && appsSink != null
                && !appsSink.isPresentationReady();
    }

    private void refreshCurrentFrame(boolean requestIfChanged) {
        if (!SecurityCenterGlassRuntimeState.isEnabled()
                || scene.scene() == SecurityCenterGlassSceneState.Scene.DETACHED) return;
        if (scene.scene() == SecurityCenterGlassSceneState.Scene.TRANSITIONING) {
            refreshTransitionFrame(turboRef.get());
            return;
        }
        reconcileSinks();
        boolean transformChanged = syncSinksFromMaterials();
        boolean includeApps = targetKind == SecurityCenterGlassSceneState.Target.ALL_APPS;
        SecurityCenterGlassFrameGeometry observed = captureFrame(includeApps);
        if (observed == null) return;
        boolean changed = transformChanged || currentFrame == null || !currentFrame.sameAs(observed);
        currentFrame = observed;
        if (requestIfChanged && (changed || requestedGeneration != scene.generation())) {
            requestCurrentGeneration(observed);
        }
    }

    private SecurityCenterGlassFrameGeometry captureFrame(boolean includeAppsIfAvailable) {
        if (dockSink == null || !dockSink.isPresentationReady()) return null;
        SecurityCenterGlassGeometry dock = dockSink != null ? dockSink.captureGeometry(rootRef.get()) : null;
        if (dock == null) return null;

        SecurityCenterGlassGeometry box = null;
        View boxView = boxRef.get();
        if (boxView != null && boxView.isAttachedToWindow()
                && boxView.getVisibility() == View.VISIBLE) {
            if (boxSink == null || !boxSink.isPresentationReady()) return null;
            box = boxSink.captureGeometry(rootRef.get());
            if (box == null) return null;
        }

        SecurityCenterGlassGeometry apps = null;
        if (includeAppsIfAvailable) {
            View appsView = appsRef.get();
            if (appsView != null && appsView.isAttachedToWindow()
                    && appsView.getVisibility() == View.VISIBLE) {
                if (appsSink == null || !appsSink.isPresentationReady()) return null;
                apps = appsSink.captureGeometry(rootRef.get());
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

    private void claimCurrentTargetsWhileCustom() {
        if (ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.VENDOR) return;
        View turbo = turboRef.get();
        View dock = dockRef.get();
        View box = boxRef.get();
        View apps = appsRef.get();
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        if (turbo == null || dock == null || bridge == null) return;
        try {
            bridge.claimCustom(turbo, dock, box, apps);
            if (!scheduleVendorClearFrameCommit()) {
                releasePanel(turbo, "dynamic clean-frame barrier unavailable");
            }
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
        if (dockSink != null && !dockSink.isDisposed()) dockSink.setAuthorizedVisible(false);
        if (boxSink != null && !boxSink.isDisposed()) boxSink.setAuthorizedVisible(false);
        if (appsSink != null && !appsSink.isDisposed()) appsSink.setAuthorizedVisible(false);
    }

    private void hideAndRestoreVendor() {
        hideCustomOnly();
        boolean wasCustom = ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR;
        View ownedTurbo = customOwnerTurboRef.get();
        vendorClearCommitPending = false;
        vendorClearCommitEpoch++;
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
        vendorClearCommitPending = false;
        vendorClearCommitEpoch++;
    }

    private void disposeSinks() {
        SecurityCenterGlassSinkView oldDock = dockSink;
        SecurityCenterGlassSinkView oldBox = boxSink;
        SecurityCenterGlassSinkView oldApps = appsSink;
        dockSink = null;
        boxSink = null;
        appsSink = null;
        if (oldDock != null) oldDock.dispose();
        if (oldBox != null) oldBox.dispose();
        if (oldApps != null) oldApps.dispose();
    }

    private void cleanupViewObservers(boolean removeTurboListener) {
        cleanupRootObserverOnly();
        if (attachedRoot != null) {
            try { attachedRoot.removeOnAttachStateChangeListener(rootAttachListener); }
            catch (Throwable ignored) {}
            attachedRoot = null;
        }
        disposeSinks();
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
