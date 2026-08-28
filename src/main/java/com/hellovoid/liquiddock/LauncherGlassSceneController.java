package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Sole owner of Workspace glass visibility, bootstrap freshness and scene generation. */
final class LauncherGlassSceneController {
    private static final String TAG = "[DC][GlassScene]";
    private static final WeakHashMap<View, LauncherGlassSceneController> BY_ROOT = new WeakHashMap<>();
    private static boolean vendorRecentsCovered;
    private static boolean vendorFolderCovered;
    private static boolean vendorHomeTransitionPending;
    private static boolean vendorUnlockTransitionPending;

    enum State { DETACHED, BOOTSTRAPPING, HOME_WAITING_FRESH_FRAME, HOME_VISIBLE, COVERED }

    /** Pure state machine kept Android-free for deterministic lifecycle tests. */
    static final class StateMachine {
        private State state = State.DETACHED;
        private long generation = 1L;
        private boolean fadeAfterFreshFrame;
        private boolean fadeRevealReady;

        void onRootReady() {
            if (state == State.DETACHED) {
                state = State.BOOTSTRAPPING;
                fadeAfterFreshFrame = true;
            }
        }

        void onBootstrapReconciled() {
            if (state == State.BOOTSTRAPPING) {
                state = State.HOME_WAITING_FRESH_FRAME;
                fadeAfterFreshFrame = true;
            }
        }

        void setCovered(boolean nextCovered) {
            boolean isCovered = state == State.COVERED;
            if (isCovered == nextCovered) return;
            if (nextCovered) {
                state = State.COVERED;
                fadeAfterFreshFrame = false;
                fadeRevealReady = false;
            } else {
                generation++;
                state = State.HOME_WAITING_FRESH_FRAME;
                fadeAfterFreshFrame = true;
            }
        }

        void onGenerationInvalidated() {
            generation++;
            if (state != State.COVERED && state != State.DETACHED) {
                state = State.HOME_WAITING_FRESH_FRAME;
            }
        }

        void onFreshFrameReady(long frameGeneration) {
            if (frameGeneration != generation || state == State.COVERED || state == State.DETACHED) {
                return;
            }
            state = State.HOME_VISIBLE;
            fadeRevealReady = fadeAfterFreshFrame;
            fadeAfterFreshFrame = false;
        }

        void detach() {
            state = State.DETACHED;
            fadeAfterFreshFrame = false;
            fadeRevealReady = false;
        }

        long generation() { return generation; }
        boolean isLayerVisible() { return state == State.HOME_VISIBLE; }
        boolean consumeFadeReveal() {
            boolean result = fadeRevealReady;
            fadeRevealReady = false;
            return result;
        }
        State state() { return state; }
    }

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final StateMachine state = new StateMachine();
    private final LauncherWallpaperContentState wallpaperContentState =
            new LauncherWallpaperContentState();
    private volatile LiquidDockConfig.Glass glassConfig;
    private boolean folderCovered;
    private boolean recentsCovered;
    private boolean homeTransitionPending;
    private boolean unlockTransitionPending;
    private LauncherGlassStaticLayer layer;
    private boolean bootstrapPosted;

    // Wallpaper semantics stay here rather than in the generic PassBlur Session. Only one
    // wallpaper-labelled producer pulse may be in flight for a root at a time; a newer content
    // generation waits until the older pulse's next OES frame has been consumed.
    private long wallpaperPulseGeneration = -1L;
    private boolean wallpaperPulseAuthoritative;
    private boolean wallpaperPulseInFlight;
    private LauncherWallpaperContentState.Pulse deferredWallpaperPulse =
            LauncherWallpaperContentState.Pulse.none();

    private LauncherGlassSceneController(View root, LauncherGlassSession session,
                                         LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        this.session = session;
        this.glassConfig = glassConfig;
    }

    static synchronized LauncherGlassSceneController acquire(
            View root, LauncherGlassSession session, LiquidDockConfig.Glass glassConfig) {
        if (root == null || session == null) return null;
        LauncherGlassSceneController current = BY_ROOT.get(root);
        if (current != null && current.session == session) {
            current.glassConfig = glassConfig;
            return current;
        }
        if (current != null) current.dispose();
        LauncherGlassSceneController created =
                new LauncherGlassSceneController(root, session, glassConfig);
        created.recentsCovered = vendorRecentsCovered;
        created.folderCovered = vendorFolderCovered;
        created.homeTransitionPending = vendorHomeTransitionPending;
        created.unlockTransitionPending = vendorUnlockTransitionPending;
        if (created.recentsCovered || created.folderCovered) {
            created.state.setCovered(true);
        }
        BY_ROOT.put(root, created);
        return created;
    }

    static synchronized LauncherGlassSceneController find(View anyView) {
        View root = LauncherGlassSessionRegistry.resolveStableRoot(anyView);
        return root != null ? BY_ROOT.get(root) : null;
    }

    static synchronized LauncherGlassSceneController findRoot(View root) {
        return root != null ? BY_ROOT.get(root) : null;
    }

    static synchronized boolean isCoveredForRoot(View root) {
        LauncherGlassSceneController controller = root != null ? BY_ROOT.get(root) : null;
        return controller != null && controller.state.state() == State.COVERED;
    }

    static void setWorkspaceCovered(View anyView, boolean covered) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.setFolderCovered(covered);
    }

    static void setRecentsCoveredForAll(boolean covered) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorRecentsCovered = covered;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setRecentsCovered(covered);
        }
    }

    static void setFolderCoveredForAll(boolean covered) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorFolderCovered = covered;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setFolderCovered(covered);
        }
    }

    static void setHomeTransitionPendingForAll(boolean pending) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorHomeTransitionPending = pending;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setHomeTransitionPending(pending);
        }
    }

    static void setUnlockTransitionPendingForAll(boolean pending) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorUnlockTransitionPending = pending;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setUnlockTransitionPending(pending);
        }
    }

    static void onWallpaperChangedForAll() {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.onWallpaperChanged();
        }
    }

    static void onWallpaperCandidate(View anyView) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.onWallpaperCandidateBoundary();
    }

    static void onWallpaperAuthoritativeForAll() {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.onWallpaperAuthoritativeBoundary();
        }
    }

    static void onFreshFrameRendered(
            View root, long generation, long wallpaperGeneration, boolean wallpaperAuthoritative) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return;
        controller.onFreshFrameReady(generation);
        if (wallpaperGeneration >= 0L) {
            controller.onWallpaperFrameConsumed(
                    generation, wallpaperGeneration, wallpaperAuthoritative);
        }
    }

    static long invalidateForProducerChange(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return -1L;
        controller.deferInFlightWallpaperPulse();
        controller.state.onGenerationInvalidated();
        controller.applyLayerVisibility();
        long generation = controller.state.generation();
        if (root != null) root.postOnAnimation(() -> controller.requestFreshBackdrop(generation));
        return generation;
    }

    static void requestFreshForRoot(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller != null) controller.requestFreshBackdrop(controller.state.generation());
    }

    void onRootReady() {
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        state.onRootReady();
        if (layer == null) layer = LauncherGlassStaticLayer.acquire(root, session);
        applyLayerVisibility();
        if (isPresentationPending()) session.suspendWorkspaceProducer();
        if (bootstrapPosted) return;
        bootstrapPosted = true;
        root.postOnAnimation(() -> {
            bootstrapPosted = false;
            View liveRoot = rootRef.get();
            if (liveRoot == null || !liveRoot.isAttachedToWindow()) return;
            reconcileExistingWorkspace();
            state.onBootstrapReconciled();
            applyLayerVisibility();
            requestFreshBackdrop(state.generation());
        });
    }

    private void requestFreshBackdrop(long generation) {
        if (isPresentationPending()) return;
        if (state.state() == State.COVERED || generation != state.generation()) return;
        deferInFlightWallpaperPulse();
        session.requestFreshBackdrop(generation);
    }

    private void onFreshFrameReady(long generation) {
        state.onFreshFrameReady(generation);
        applyLayerVisibility();
        flushDeferredWallpaperPulse();
    }

    private synchronized void onWallpaperChanged() {
        long generation = wallpaperContentState.onWallpaperChanged();
        LauncherWallpaperContentState.Pulse deferred = deferredWallpaperPulse;
        if (deferred.requested() && deferred.generation < generation) {
            deferredWallpaperPulse = LauncherWallpaperContentState.Pulse.none();
        }
        MainHook.log(TAG + " wallpaper changed contentGeneration=" + generation);
    }

    private synchronized void onWallpaperCandidateBoundary() {
        long generation = wallpaperContentState.generation();
        requestWallpaperPulse(wallpaperContentState.onCandidateBoundary(generation));
    }

    private synchronized void onWallpaperAuthoritativeBoundary() {
        long generation = wallpaperContentState.generation();
        requestWallpaperPulse(wallpaperContentState.onAuthoritativeBoundary(generation));
    }

    private synchronized void onWallpaperFrameConsumed(
            long sceneGeneration, long contentGeneration, boolean authoritative) {
        if (!wallpaperPulseInFlight
                || contentGeneration != wallpaperPulseGeneration
                || authoritative != wallpaperPulseAuthoritative) {
            return;
        }

        wallpaperPulseGeneration = -1L;
        wallpaperPulseAuthoritative = false;
        wallpaperPulseInFlight = false;

        LauncherWallpaperContentState.Pulse followUp = LauncherWallpaperContentState.Pulse.none();
        if (authoritative) {
            if (wallpaperContentState.onFrameCommitted(contentGeneration, true)) {
                MainHook.log(TAG + " wallpaper committed contentGeneration=" + contentGeneration
                        + " sceneGeneration=" + sceneGeneration);
            }
        } else {
            followUp = wallpaperContentState.onCandidateFrameConsumed(contentGeneration);
        }

        LauncherWallpaperContentState.Pulse deferred = deferredWallpaperPulse;
        deferredWallpaperPulse = LauncherWallpaperContentState.Pulse.none();
        if (followUp.requested()) {
            requestWallpaperPulse(followUp);
        } else if (deferred.requested()
                && deferred.generation == wallpaperContentState.generation()) {
            requestWallpaperPulse(deferred);
        }
    }

    private synchronized void deferInFlightWallpaperPulse() {
        if (!wallpaperPulseInFlight) return;
        long generation = wallpaperPulseGeneration;
        boolean authoritative = wallpaperPulseAuthoritative;
        if (generation == wallpaperContentState.generation()) {
            deferredWallpaperPulse = LauncherWallpaperContentState.Pulse.request(
                    generation, authoritative);
        }
        wallpaperPulseGeneration = -1L;
        wallpaperPulseAuthoritative = false;
        wallpaperPulseInFlight = false;
        session.cancelWallpaperBackdrop(generation);
    }

    private synchronized void flushDeferredWallpaperPulse() {
        if (isPresentationPending()) return;
        if (state.state() == State.COVERED || wallpaperPulseInFlight) return;
        LauncherWallpaperContentState.Pulse deferred = deferredWallpaperPulse;
        if (!deferred.requested()
                || deferred.generation != wallpaperContentState.generation()) return;
        deferredWallpaperPulse = LauncherWallpaperContentState.Pulse.none();
        requestWallpaperPulse(deferred);
    }

    private boolean isPresentationPending() {
        return homeTransitionPending || unlockTransitionPending;
    }

    private void setHomeTransitionPending(boolean pending) {
        boolean wasPending = isPresentationPending();
        homeTransitionPending = pending;
        onPresentationPendingChanged(wasPending, isPresentationPending(), "home");
    }

    private void setUnlockTransitionPending(boolean pending) {
        boolean wasPending = isPresentationPending();
        unlockTransitionPending = pending;
        onPresentationPendingChanged(wasPending, isPresentationPending(), "unlock");
    }

    private void onPresentationPendingChanged(boolean wasPending, boolean pending, String reason) {
        if (wasPending == pending) return;
        if (pending) {
            deferInFlightWallpaperPulse();
            state.onGenerationInvalidated();
            applyLayerVisibility();
            session.suspendWorkspaceProducer();
            MainHook.log(TAG + " presentation pending reason=" + reason
                    + " generation=" + state.generation());
            return;
        }
        if (state.state() != State.COVERED && state.state() != State.DETACHED) {
            MainHook.log(TAG + " presentation settled reason=" + reason
                    + " generation=" + state.generation());
            requestFreshBackdrop(state.generation());
        }
    }

    private void setFolderCovered(boolean covered) {
        folderCovered = covered;
        setEffectiveCovered(folderCovered || recentsCovered);
    }

    private void setRecentsCovered(boolean covered) {
        if (recentsCovered == covered) return;
        recentsCovered = covered;
        setEffectiveCovered(folderCovered || recentsCovered);
    }

    private void setEffectiveCovered(boolean covered) {
        boolean wasCovered = state.state() == State.COVERED;
        state.setCovered(covered);
        applyLayerVisibility();
        if (covered) {
            session.suspendWorkspaceProducer();
        } else if (wasCovered) {
            // Scene recovery wins. Any wallpaper pulse that was in flight is deferred by
            // requestFreshBackdrop() and is released only after this generic fresh frame lands.
            requestFreshBackdrop(state.generation());
        }
    }

    private synchronized void requestWallpaperPulse(LauncherWallpaperContentState.Pulse pulse) {
        if (pulse == null || !pulse.requested()
                || pulse.generation != wallpaperContentState.generation()) return;
        if (state.state() == State.COVERED || isPresentationPending() || wallpaperPulseInFlight) {
            LauncherWallpaperContentState.Pulse deferred = deferredWallpaperPulse;
            if (!deferred.requested() || pulse.generation >= deferred.generation) {
                deferredWallpaperPulse = pulse;
            }
            return;
        }
        wallpaperPulseGeneration = pulse.generation;
        wallpaperPulseAuthoritative = pulse.authoritative;
        wallpaperPulseInFlight = true;
        if (!session.requestWallpaperBackdrop(
                state.generation(), pulse.generation, pulse.authoritative)) {
            wallpaperPulseGeneration = -1L;
            wallpaperPulseAuthoritative = false;
            wallpaperPulseInFlight = false;
            deferredWallpaperPulse = pulse;
            return;
        }
        MainHook.log(TAG + " wallpaper pulse contentGeneration=" + pulse.generation
                + " authoritative=" + pulse.authoritative
                + " sceneGeneration=" + state.generation());
    }

    private void applyLayerVisibility() {
        LauncherGlassStaticLayer current = layer;
        if (current != null) {
            current.setSceneVisible(state.isLayerVisible(), state.consumeFadeReveal(), folderCovered);
        }
    }

    /** Startup barrier: scan objects that existed before constructor/attach hooks became useful. */
    private void reconcileExistingWorkspace() {
        View root = rootRef.get();
        if (root == null) return;
        View workspace = findWorkspace(root);
        scan(workspace != null ? workspace : root);
        MainHook.log(TAG + " bootstrap reconciled generation=" + state.generation());
    }

    private void scan(View view) {
        if (view == null || view == layer) return;
        LiquidDockConfig.Glass config = glassConfig;
        if (config != null) {
            MiuixFolderGlassHook.reconcileExistingView(view, config);
            MiuixLauncherStaticGlassHook.reconcileExistingHost(view, config);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
        }
    }

    private static View findWorkspace(View view) {
        if (view == null) return null;
        String simple = view.getClass().getSimpleName();
        if ("Workspace".equals(simple)
                || "com.miui.home.launcher.Workspace".equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findWorkspace(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    void dispose() {
        View root = rootRef.get();
        synchronized (LauncherGlassSceneController.class) {
            if (root != null && BY_ROOT.get(root) == this) BY_ROOT.remove(root);
        }
        synchronized (this) {
            wallpaperPulseGeneration = -1L;
            wallpaperPulseAuthoritative = false;
            wallpaperPulseInFlight = false;
            deferredWallpaperPulse = LauncherWallpaperContentState.Pulse.none();
        }
        state.detach();
        LauncherGlassStaticLayer current = layer;
        layer = null;
        if (current != null) current.dispose();
    }
}
