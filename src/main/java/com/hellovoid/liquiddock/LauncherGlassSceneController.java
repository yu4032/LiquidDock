package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Sole owner of Workspace glass visibility, bootstrap freshness and scene generation. */
final class LauncherGlassSceneController {
    private static final String TAG = "[DC][GlassScene]";
    private static final WeakHashMap<View, LauncherGlassSceneController> BY_ROOT = new WeakHashMap<>();

    enum State { DETACHED, BOOTSTRAPPING, HOME_WAITING_FRESH_FRAME, HOME_VISIBLE, COVERED }

    /** Pure state machine kept Android-free for deterministic lifecycle tests. */
    static final class StateMachine {
        private State state = State.DETACHED;
        private long generation = 1L;
        private boolean covered;

        void onRootReady() {
            if (state == State.DETACHED) state = State.BOOTSTRAPPING;
        }

        void onBootstrapReconciled() {
            if (!covered && state == State.BOOTSTRAPPING) state = State.HOME_WAITING_FRESH_FRAME;
        }

        void setCovered(boolean nextCovered) {
            if (covered == nextCovered) return;
            covered = nextCovered;
            if (covered) {
                state = State.COVERED;
            } else {
                generation++;
                state = State.HOME_WAITING_FRESH_FRAME;
            }
        }

        void onGenerationInvalidated() {
            generation++;
            if (!covered && state != State.DETACHED) state = State.HOME_WAITING_FRESH_FRAME;
        }

        void onFreshFrameReady(long frameGeneration) {
            if (frameGeneration != generation || covered || state == State.DETACHED) return;
            state = State.HOME_VISIBLE;
        }

        void detach() {
            state = State.DETACHED;
            covered = false;
        }

        long generation() { return generation; }
        boolean isLayerVisible() { return state == State.HOME_VISIBLE; }
        State state() { return state; }
    }

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final StateMachine state = new StateMachine();
    private volatile LiquidDockConfig.Glass glassConfig;
    private WeakReference<View> recentsRef = new WeakReference<>(null);
    private boolean folderCovered;
    private boolean recentsCovered;
    private LauncherGlassStaticLayer layer;
    private boolean bootstrapPosted;

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

    static void setWorkspaceCovered(View anyView, boolean covered) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.setFolderCovered(covered);
    }

    static void bindRecentsView(View anyView, View recents) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.recentsRef = new WeakReference<>(recents);
    }

    static void syncRecentsForRoot(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return;
        View recents = controller.recentsRef.get();
        boolean covered = recents != null && recents.getVisibility() == View.VISIBLE && recents.isShown();
        controller.setRecentsCovered(covered);
    }

    static void onFreshFrameRendered(View root, long generation) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller != null) controller.onFreshFrameReady(generation);
    }

    static long invalidateForProducerChange(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return -1L;
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
        if (state.state() == State.COVERED || generation != state.generation()) return;
        session.requestFreshBackdrop(generation);
    }

    private void onFreshFrameReady(long generation) {
        state.onFreshFrameReady(generation);
        applyLayerVisibility();
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
            requestFreshBackdrop(state.generation());
        }
    }

    private void applyLayerVisibility() {
        LauncherGlassStaticLayer current = layer;
        if (current != null) current.setSceneVisible(state.isLayerVisible());
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
        state.detach();
        LauncherGlassStaticLayer current = layer;
        layer = null;
        if (current != null) current.dispose();
    }
}
