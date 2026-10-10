package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** One shared GPU glass session and one scene controller per stable Launcher ViewRoot. */
final class LauncherGlassSessionRegistry {
    interface RolloverCompletion {
        void onComplete(boolean success);
    }

    private static final WeakHashMap<View, LauncherGlassSession> SESSIONS = new WeakHashMap<>();
    private static long workstationRolloverGeneration;
    private static boolean nativeTransitionFrameSyncActive;

    private LauncherGlassSessionRegistry() {}

    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled()) return null;
        LiquidDockConfig.Glass liveGlass = LiveGlassConfigState.currentGlass();
        if (liveGlass != null) glassConfig = liveGlass;
        View root = resolveStableRoot(materialHost);
        if (root == null) return null;
        LauncherGlassSession current = SESSIONS.get(root);
        if (current != null && !current.isShutdown()) {
            current.setGlassConfig(glassConfig);
            LauncherGlassSceneController controller =
                    LauncherGlassSceneController.acquire(root, current, glassConfig);
            if (controller != null) controller.onRootReady();
            return current;
        }
        LauncherGlassSession created = new LauncherGlassSession(root, glassConfig);
        SESSIONS.put(root, created);
        if (nativeTransitionFrameSyncActive) {
            created.setNativeTransitionFrameSyncEnabled(true, "native-transition-inherited");
        }
        LauncherGlassSceneController controller =
                LauncherGlassSceneController.acquire(root, created, glassConfig);
        if (controller != null) controller.onRootReady();
        return created;
    }

    /** Existing Workspace native producer only. A Recents capsule must never bind another one. */
    static synchronized LauncherGlassSession existingRootSource(View root) {
        LauncherGlassSession session = root != null ? SESSIONS.get(root) : null;
        return session != null && !session.isShutdown() ? session : null;
    }

    static synchronized void traceWallpaperReturnForAll(String phase, long serial) {
        if (!MainHook.debugLogging) return;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session != null && !session.isShutdown()) {
                session.traceWallpaperReturn(phase, serial);
            }
        }
    }

    /** Update existing root owners without acquiring new surfaces or reattaching nodes. */
    static synchronized void applyLiveGlassConfigToAll(LiquidDockConfig.Glass glassConfig) {
        for (java.util.Map.Entry<View, LauncherGlassSession> entry
                : new ArrayList<>(SESSIONS.entrySet())) {
            LauncherGlassSession session = entry.getValue();
            if (session == null || session.isShutdown()) continue;
            LauncherGlassSceneController.applyLiveGlassConfigForRoot(entry.getKey(), glassConfig);
            session.applyLiveGlassConfig(glassConfig);
        }
    }

    static View resolveStableRoot(View materialHost) {
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || materialHost.getWidth() <= 0 || materialHost.getHeight() <= 0) return null;
        View root = materialHost.getRootView();
        if (root == null || root == materialHost || !root.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0 || root.getWindowToken() == null) {
            return null;
        }
        return root;
    }

    static synchronized void setNativeTransitionFrameSyncForAll(
            boolean enabled, String reason) {
        if (nativeTransitionFrameSyncActive == enabled) return;
        nativeTransitionFrameSyncActive = enabled;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            session.setNativeTransitionFrameSyncEnabled(enabled, reason);
        }
    }

    static synchronized void setUnlockTransitionFrameSyncForAll(
            boolean enabled, String reason) {
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            session.setUnlockTransitionFrameSyncEnabled(enabled, reason);
        }
    }

    /** Stop every existing Launcher PassBlur producer as soon as unlock presentation starts. */
    static synchronized void suspendForUnlockCapture() {
        int paused = 0;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            try {
                if (session.suspendProducerForUnlockCapture()) paused++;
            } catch (Throwable error) {
                MainHook.log("[DC][LauncherGlass] unlock producer pause failed: " + error);
            }
        }
        MainHook.log("[DC][LauncherGlass] unlock producer capture suspended sessions=" + paused);
    }

    /**
     * Roll all live OES/SurfaceTexture endpoints after the vendor unlock animation. Completion is
     * delivered exactly once after every live session either finishes endpoint replacement or
     * rejects/fails. A successful callback means endpoint rollover only; scene freshness remains
     * owned by LauncherGlassSceneController.
     */
    static void prepareUnlockCaptureReturn(RolloverCompletion completion) {
        ArrayList<LauncherGlassSession> sessions;
        synchronized (LauncherGlassSessionRegistry.class) {
            sessions = new ArrayList<>(SESSIONS.values());
        }
        sessions.removeIf(session -> session == null || session.isShutdown());
        if (sessions.isEmpty()) {
            if (completion != null) completion.onComplete(true);
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        AtomicInteger remaining = new AtomicInteger(sessions.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        RolloverCompletion completeOne = sessionSuccess -> {
            if (!sessionSuccess) failed.set(true);
            if (remaining.decrementAndGet() != 0) return;
            boolean success = !failed.get();
            if (success) {
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover complete sessions="
                        + sessions.size());
            } else {
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover incomplete; capture remains blocked");
            }
            if (completion != null) completion.onComplete(success);
        };

        for (LauncherGlassSession session : sessions) {
            try {
                if (!session.rebindProducer(completeOne)) {
                    MainHook.log("[DC][LauncherGlass] unlock endpoint rollover rejected by render queue");
                    main.post(() -> completeOne.onComplete(false));
                }
            } catch (Throwable error) {
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover failed: " + error);
                main.post(() -> completeOne.onComplete(false));
            }
        }
    }

    /**
     * HyperOS Workstation can keep the same valid root Surface across Recents while silently
     * retiring the PassBlur BufferQueue producer. Return true only when every live session
     * accepts the rollover request. Actual endpoint recreation is logged asynchronously by the
     * session; scene freshness remains owned by LauncherGlassSceneController.
     */
    static synchronized boolean prepareWorkstationRecentsReturn() {
        if (!MainHook.isWorkstationMode()) return true;
        long generation = ++workstationRolloverGeneration;
        int live = 0;
        int accepted = 0;
        int rejected = 0;
        int failed = 0;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            live++;
            try {
                if (session.rebindWorkstationProducer("workstation-recents", generation)) accepted++;
                else rejected++;
            } catch (Throwable error) {
                failed++;
                MainHook.log("[DC][LauncherGlass][ProducerRecovery] reason=workstation-recents"
                        + " session=" + session.diagnosticSessionId()
                        + " generation=" + generation
                        + " result=FAILED stage=request error=" + error);
            }
        }
        String result = failed > 0 ? "FAILED" : rejected > 0 ? "REJECTED" : "ACCEPTED";
        MainHook.log("[DC][LauncherGlass][ProducerRecovery] reason=workstation-recents"
                + " session=aggregate generation=" + generation
                + " result=" + result + " stage=request"
                + " accepted=" + accepted + " rejected=" + rejected
                + " failed=" + failed + " total=" + live);
        return rejected == 0 && failed == 0;
    }

    static synchronized void shutdownAll() {
        ArrayList<View> roots = new ArrayList<>(SESSIONS.keySet());
        ArrayList<LauncherGlassSession> sessions = new ArrayList<>(SESSIONS.values());
        for (View root : roots) {
            LauncherGlassSceneController controller = LauncherGlassSceneController.findRoot(root);
            if (controller != null) controller.dispose();
        }
        for (LauncherGlassSession session : sessions) {
            if (session != null) session.shutdown();
        }
        SESSIONS.clear();
    }

    static synchronized void forget(View root, LauncherGlassSession session) {
        if (root == null || session == null) return;
        if (SESSIONS.get(root) == session) {
            SESSIONS.remove(root);
            LauncherGlassSceneController controller = LauncherGlassSceneController.findRoot(root);
            if (controller != null) controller.dispose();
        }
    }
}
