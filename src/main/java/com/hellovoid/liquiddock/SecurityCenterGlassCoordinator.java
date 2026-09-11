package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
    private boolean handoffPending;
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
            syncSinksFromMaterials();
            if (ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR
                    && ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.CUSTOM_CLOSING) {
                handoffPending = true;
            }
        }
        log("assistant bound type=" + type
                + " box=" + (boxLayout != null ? boxLayout.getClass().getName() : "none"), null);
    }

    void updateAllAppsLayout(View turboLayout, View appsLayout) {
        if (!isCurrentTurbo(turboLayout) || appsLayout == null) return;
        appsRef = new WeakReference<>(appsLayout);
        reconcileSinks();
        syncSinksFromMaterials();
    }

    long onAllAppsToggleStarted(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return -1L;
        SecurityCenterGlassSceneState.Decision decision = scene.onTransitionStarted();
        applyDecision(decision, null);
        if (ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM) {
            handoffPending = true;
        }
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
        if (!isCurrentTurbo(turboLayout)) return;
        ownership.onVendorClosing();
        handoffPending = false;
        log("vendor panel closing; retaining presentation ownership until terminal cleanup", null);
    }

    void onVendorPanelTerminal(View turboLayout) {
        releasePanel(turboLayout, "vendor panel terminal cleanup");
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

        SecurityCenterMaterialOwnershipState.Owner owner = ownership.owner();
        if (owner == SecurityCenterMaterialOwnershipState.Owner.VENDOR) return;
        if (owner == SecurityCenterMaterialOwnershipState.Owner.CUSTOM_CLOSING
                && !ownership.hasSuppressedVendor()) {
            hideCustomOnly();
            return;
        }

        boolean transitioning = scene.scene() == SecurityCenterGlassSceneState.Scene.TRANSITIONING;
        SecurityCenterGlassSceneState.Decision decision = transitioning
                ? null : scene.onFreshFrameRendered(generation);
        if (!transitioning && (decision == null || !decision.revealCustom)
                && owner == SecurityCenterMaterialOwnershipState.Owner.CUSTOM_PREPARING) return;

        setAuthorizedSinksForFrame(currentFrame);
        if (owner == SecurityCenterMaterialOwnershipState.Owner.CUSTOM_PREPARING || handoffPending) {
            if (!completePresentationHandoff(generation, transitioning ? "transition" : "scene")) {
                return;
            }
        }
        renderedGeneration = generation;
        log("current-generation frame presented generation=" + generation
                + " scene=" + scene.scene()
                + " owner=" + ownership.owner()
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
        dockSink = reconcileSink(dockSink, dockRef.get(), live);
        boxSink = reconcileSink(boxSink, boxRef.get(), live);
        appsSink = reconcileSink(appsSink, appsRef.get(), live);
    }

    private SecurityCenterGlassSinkView reconcileSink(
            SecurityCenterGlassSinkView current,
            View material,
            SecurityCenterGlassSession live) {
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
        return SecurityCenterGlassSinkView.attachBefore(material, live);
    }

    private float resolveLiveDockCornerRadius() {
        View dock = dockRef.get();
        if (dock == null || dock.getWidth() <= 0 || dock.getHeight() <= 0
                || !dock.getClipToOutline()) return Float.NaN;
        ViewOutlineProvider provider = dock.getOutlineProvider();
        if (provider == null) return Float.NaN;
        try {
            Outline outline = new Outline();
            provider.getOutline(dock, outline);
            Rect bounds = new Rect();
            if (!outline.getRect(bounds)) return Float.NaN;
            float radius = outline.getRadius();
            if (!Float.isFinite(radius) || radius <= 0f
                    || bounds.left != 0 || bounds.top != 0
                    || bounds.right != dock.getWidth() || bounds.bottom != dock.getHeight()) {
                return Float.NaN;
            }
            return radius;
        } catch (Throwable error) {
            log("live Dock outline unavailable", error);
            return Float.NaN;
        }
    }

    private float resolveStaticCornerRadius(View target, int exactRadiusResId) {
        if (target == null || exactRadiusResId == 0) return Float.NaN;
        float radius = target.getResources().getDimension(exactRadiusResId);
        return Float.isFinite(radius) && radius > 0f ? radius : Float.NaN;
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

        if (decision.claimCustomOwnership && decision.generation == scene.generation()) {
            prepareCustomOwnershipForPresentation();
        }
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

    private void prepareCustomOwnershipForPresentation() {
        if (ownership.owner() != SecurityCenterMaterialOwnershipState.Owner.VENDOR) return;
        hideCustomOnly();
        ownership.onCustomPreparing();
        handoffPending = true;
        customOwnerTurboRef = new WeakReference<>(null);
        renderedGeneration = -1L;
        requestedGeneration = -1L;
        log("custom presentation preparing; vendor remains authoritative generation="
                + scene.generation(), null);
    }

    private boolean completePresentationHandoff(long generation, String reason) {
        if (generation != scene.generation()
                || ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM_CLOSING) {
            handoffPending = false;
            return false;
        }
        View turbo = turboRef.get();
        View dock = dockRef.get();
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        SecurityCenterGlassFrameGeometry frame = currentFrame;
        if (turbo == null || dock == null || bridge == null || frame == null) {
            hideCustomOnly();
            return false;
        }
        View box = frame.boxGeometry() != null ? boxRef.get() : null;
        View apps = frame.appsGeometry() != null ? appsRef.get() : null;
        boolean firstHandoff = !ownership.hasSuppressedVendor();
        renderedGeneration = generation;
        try {
            if (firstHandoff) ownership.onCustomPresented();
            bridge.claimCustom(turbo, dock, box, apps);
            customOwnerTurboRef = new WeakReference<>(turbo);
            handoffPending = false;
            log("current-generation presentation handed off generation=" + generation
                    + " " + reason + " nodes=" + frame.nodeCount(), null);
            return true;
        } catch (Throwable error) {
            hideCustomOnly();
            try { bridge.restoreVendor(turbo); }
            catch (Throwable restoreError) { log("handoff rollback restore failed", restoreError); }
            ownership.releaseToVendor();
            renderedGeneration = -1L;
            customOwnerTurboRef = new WeakReference<>(null);
            handoffPending = false;
            scene.onTerminalFailure();
            releasePanel(turbo, "custom handoff failed");
            log("presentation handoff failed closed", error);
            return false;
        }
    }

    private void requestCurrentGeneration(SecurityCenterGlassFrameGeometry frame) {
        SecurityCenterGlassSession live = session;
        View root = rootRef.get();
        long generation = scene.generation();
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
        View root = rootRef.get();
        if (dockSink == null || !dockSink.isPresentationReady()) return null;
        float dockRadius = resolveLiveDockCornerRadius();
        if (!Float.isFinite(dockRadius) || dockRadius <= 0f) return null;
        SecurityCenterGlassGeometry dock = dockSink.captureGeometry(root, dockRadius);
        if (dock == null) return null;

        SecurityCenterGlassGeometry box = null;
        View boxView = boxRef.get();
        if (boxView != null && boxView.isAttachedToWindow()
                && boxView.getVisibility() == View.VISIBLE) {
            if (boxSink == null || !boxSink.isPresentationReady()) return null;
            int boxRadiusResId = assistantType == ASSISTANT_GAME
                    ? gameToolboxCornerRadiusResId
                    : assistantType == ASSISTANT_VIDEO ? allAppsCornerRadiusResId : 0;
            float boxRadius = resolveStaticCornerRadius(boxView, boxRadiusResId);
            if (!Float.isFinite(boxRadius) || boxRadius <= 0f) return null;
            box = boxSink.captureGeometry(root, boxRadius);
            if (box == null) return null;
        }

        SecurityCenterGlassGeometry apps = null;
        if (includeAppsIfAvailable) {
            View appsView = appsRef.get();
            if (appsView != null && appsView.isAttachedToWindow()
                    && appsView.getVisibility() == View.VISIBLE) {
                if (appsSink == null || !appsSink.isPresentationReady()) return null;
                float appsRadius = resolveStaticCornerRadius(appsView, allAppsCornerRadiusResId);
                if (!Float.isFinite(appsRadius) || appsRadius <= 0f) return null;
                apps = appsSink.captureGeometry(root, appsRadius);
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
        ownership.releaseToVendor();
        handoffPending = false;
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
        handoffPending = false;
        renderedGeneration = -1L;
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
