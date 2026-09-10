package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/** Coordinates one Security Center Global Dock / All Apps glass session for the active root. */
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

    private final Policy policy = new Policy();
    private final SecurityCenterGlassSceneState scene = new SecurityCenterGlassSceneState();
    private final SecurityCenterMaterialOwnershipState ownership =
            new SecurityCenterMaterialOwnershipState();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LiquidDockConfig.Glass glassConfig;
    private final SecurityCenterVendorMaterialBridge vendorMaterialBridge;

    private WeakReference<View> turboRef = new WeakReference<>(null);
    private WeakReference<View> dockRef = new WeakReference<>(null);
    private WeakReference<View> appsRef = new WeakReference<>(null);
    private WeakReference<View> rootRef = new WeakReference<>(null);
    private WeakReference<View> targetRef = new WeakReference<>(null);
    private WeakReference<View> customOwnerTurboRef = new WeakReference<>(null);
    private SecurityCenterGlassSceneState.Target targetKind =
            SecurityCenterGlassSceneState.Target.DOCK;
    private SecurityCenterGlassGeometry currentGeometry;
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
                    // Root authority, not a transient TurboLayout detach, owns session teardown.
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
        this(glassConfig, null);
    }

    SecurityCenterGlassCoordinator(
            LiquidDockConfig.Glass glassConfig,
            SecurityCenterVendorMaterialBridge vendorMaterialBridge) {
        this.glassConfig = glassConfig;
        this.vendorMaterialBridge = vendorMaterialBridge;
        SecurityCenterGlassRuntimeState.setOwner(this);
    }

    void bindGlobalDock(View turboLayout, View dockLayout) {
        if (turboLayout == null || dockLayout == null) return;
        View previousTurbo = turboRef.get();
        if (previousTurbo != null && previousTurbo != turboLayout
                && ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM) {
            hideAndRestoreVendor();
        }
        turboRef = new WeakReference<>(turboLayout);
        dockRef = new WeakReference<>(dockLayout);
        // c0() starts from R(), which disposes the vendor All Apps instance for this dock cycle.
        appsRef = new WeakReference<>(null);
        targetRef = new WeakReference<>(dockLayout);
        targetKind = SecurityCenterGlassSceneState.Target.DOCK;
        observeTurboAttach(turboLayout);
        if (!SecurityCenterGlassRuntimeState.isEnabled()) {
            releaseAll();
            return;
        }
        if (turboLayout.isAttachedToWindow()) bindAttachedRoot(turboLayout);
    }

    void updateAllAppsLayout(View turboLayout, View appsLayout) {
        if (!isCurrentTurbo(turboLayout) || appsLayout == null) return;
        appsRef = new WeakReference<>(appsLayout);
    }

    void onAllAppsToggleStarted(View turboLayout) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return;
        applyDecision(scene.onTransitionStarted(), null);
    }

    void onAllAppsToggleSettled(View turboLayout, boolean allAppsPresent) {
        if (!isCurrentTurbo(turboLayout) || !SecurityCenterGlassRuntimeState.isEnabled()) return;
        targetKind = allAppsPresent
                ? SecurityCenterGlassSceneState.Target.ALL_APPS
                : SecurityCenterGlassSceneState.Target.DOCK;
        View target = allAppsPresent ? appsRef.get() : dockRef.get();
        if (target == null) {
            releaseAll();
            log("settled target missing; failed closed target=" + targetKind, null);
            return;
        }
        targetRef = new WeakReference<>(target);
        SecurityCenterGlassGeometry geometry = captureGeometry(target);
        if (geometry != null) currentGeometry = geometry;
        applyDecision(scene.onGeometrySettled(targetKind), geometry);
    }

    boolean shouldSuppressVendorFinalBackground(Object turboLayout) {
        return turboLayout != null
                && turboLayout == turboRef.get()
                && ownership.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM
                && ownership.canSuppressVendor(renderedGeneration, scene.generation())
                && policy.currentSession() == session
                && SecurityCenterGlassRuntimeState.isEnabled();
    }

    @Override
    public void releaseAll() {
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
        rootRef = new WeakReference<>(null);
        targetRef = new WeakReference<>(null);
        currentGeometry = null;
        renderedGeneration = -1L;
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
        SecurityCenterVendorMaterialBridge bridge = vendorMaterialBridge;
        if (turbo == null || dock == null || bridge == null) return;
        try {
            bridge.claimCustom(turbo, dock);
        } catch (Throwable error) {
            hideCustomOnly();
            ownership.releaseToVendor();
            renderedGeneration = -1L;
            try {
                bridge.restoreVendor(turbo);
            } catch (Throwable restoreError) {
                log("claim rollback restore failed", restoreError);
            }
            log("custom material claim failed closed", error);
            return;
        }

        ownership.onCustomClaimed();
        customOwnerTurboRef = new WeakReference<>(turbo);
        renderedGeneration = generation;
        SecurityCenterGlassOutputView current = output;
        if (current != null && !current.isDisposed()) current.setAuthorizedVisible(true);
        log("current-generation scene revealed generation=" + generation
                + " scene=" + scene.scene(), null);
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

        SecurityCenterGlassSceneState.Decision decision = scene.onTerminalFailure();
        hideAndRestoreVendor();
        ReleaseDecision release = policy.releaseAll();
        cleanupViewObservers(false);
        SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) release.sessionToShutdown : callbackSession;
        session = null;
        if (decision.shutdownSession && old != null) old.shutdown();
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
            refreshCurrentGeometry(true);
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
        SecurityCenterGlassGeometry geometry = captureGeometry(dockRef.get());
        if (geometry != null) currentGeometry = geometry;
        applyDecision(
                scene.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK),
                geometry);
    }

    private void ensureOutputSibling(View turboLayout) {
        SecurityCenterGlassSession live = session;
        if (live == null || live.isShutdown() || turboLayout == null
                || !(turboLayout.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) turboLayout.getParent();
        SecurityCenterGlassOutputView current = output;
        if (current != null && !current.isDisposed() && current.getParent() == parent
                && parent.indexOfChild(current) == parent.indexOfChild(turboLayout) - 1) {
            return;
        }
        if (current != null) current.dispose();
        output = SecurityCenterGlassOutputView.attachBefore(turboLayout, live);
    }

    private void applyDecision(
            SecurityCenterGlassSceneState.Decision decision,
            SecurityCenterGlassGeometry geometry) {
        if (decision == null) return;
        if (decision.hideCustom || decision.releaseCustomOwnership) hideAndRestoreVendor();
        if (decision.requestFresh) {
            SecurityCenterGlassSession live = session;
            SecurityCenterGlassGeometry next = geometry != null ? geometry : currentGeometry;
            View root = rootRef.get();
            if (live != null && !live.isShutdown() && root != null
                    && policy.currentSession() == live && policy.currentRoot() == root
                    && next != null && decision.generation == scene.generation()) {
                live.requestFresh(decision.generation, next);
            }
        }
        if (decision.shutdownSession) {
            ReleaseDecision release = policy.releaseAll();
            SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                    ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
            session = null;
            if (old != null) old.shutdown();
        }
    }

    private void refreshCurrentGeometry(boolean requestIfChanged) {
        if (!SecurityCenterGlassRuntimeState.isEnabled()
                || scene.scene() == SecurityCenterGlassSceneState.Scene.DETACHED
                || scene.scene() == SecurityCenterGlassSceneState.Scene.TRANSITIONING) return;
        View target = targetRef.get();
        SecurityCenterGlassGeometry observed = captureGeometry(target);
        if (observed == null) return;
        boolean changed = currentGeometry == null || !currentGeometry.sameAs(observed);
        currentGeometry = observed;
        if (!requestIfChanged || !changed) return;
        SecurityCenterGlassSession live = session;
        View root = rootRef.get();
        long generation = scene.generation();
        if (live != null && root != null && !live.isShutdown()
                && policy.currentSession() == live && policy.currentRoot() == root) {
            live.requestFresh(generation, observed);
        }
    }

    private SecurityCenterGlassGeometry captureGeometry(View target) {
        View root = rootRef.get();
        if (root == null || target == null || !root.isAttachedToWindow()
                || !target.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        int[] rootLocation = new int[2];
        int[] targetLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        target.getLocationOnScreen(targetLocation);
        float radius = MiuixGlassHook.readNativeOpticsRadius(target);
        if (!Float.isFinite(radius) || radius <= 0f) {
            radius = Math.min(target.getWidth(), target.getHeight()) * 0.12f;
        }
        return SecurityCenterGlassGeometry.resolve(
                root.getWidth(), root.getHeight(),
                rootLocation[0], rootLocation[1],
                targetLocation[0], targetLocation[1],
                targetLocation[0] + target.getWidth(),
                targetLocation[1] + target.getHeight(),
                radius);
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
                refreshCurrentGeometry(true);
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
            // Keep custom hidden and logical ownership released. Never synthesize guessed vendor
            // tokens/blur/shadow state when the authoritative U() restoration itself fails.
            log("vendor material restore failed", error);
        }
    }

    private void releaseForRootDetach() {
        ReleaseDecision release = policy.releaseAll();
        scene.onRootDetached();
        hideAndRestoreVendor();
        cleanupViewObservers(false);
        SecurityCenterGlassSession old = release.sessionToShutdown instanceof SecurityCenterGlassSession
                ? (SecurityCenterGlassSession) release.sessionToShutdown : session;
        session = null;
        if (old != null) old.shutdown();
        rootRef = new WeakReference<>(null);
        currentGeometry = null;
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
