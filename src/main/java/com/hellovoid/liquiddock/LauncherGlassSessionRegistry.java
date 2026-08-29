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
    private static final WeakHashMap<View, LauncherGlassSession> SESSIONS = new WeakHashMap<>();

    private LauncherGlassSessionRegistry() {}

    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled()) return null;
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
        LauncherGlassSceneController controller =
                LauncherGlassSceneController.acquire(root, created, glassConfig);
        if (controller != null) controller.onRootReady();
        return created;
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

    /** Stop every existing Launcher PassBlur producer as soon as unlock presentation starts. */
    static synchronized void suspendForUnlockCapture() {
        int paused = 0;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            try {
                Object value = HookUtil.getField(session, "binding");
                if (value instanceof Miuix307PassBlurBridge.Binding) {
                    Miuix307PassBlurBridge.pauseUpdates((Miuix307PassBlurBridge.Binding) value);
                    paused++;
                }
            } catch (Throwable error) {
                MainHook.log("[DC][LauncherGlass] unlock producer pause failed: " + error);
            }
        }
        MainHook.log("[DC][LauncherGlass] unlock producer capture suspended sessions=" + paused);
    }

    /**
     * Roll all live OES/SurfaceTexture endpoints after the vendor unlock animation. The callback
     * runs only after each session's render queue has executed the rollover task queued by
     * rebindProducer(), so the old lockscreen buffer queue cannot survive into HOME capture.
     */
    static void prepareUnlockCaptureReturn(Runnable ready) {
        ArrayList<LauncherGlassSession> sessions;
        synchronized (LauncherGlassSessionRegistry.class) {
            sessions = new ArrayList<>(SESSIONS.values());
        }
        sessions.removeIf(session -> session == null || session.isShutdown());
        if (sessions.isEmpty()) {
            if (ready != null) ready.run();
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        AtomicInteger remaining = new AtomicInteger(sessions.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        Runnable completeOne = () -> {
            if (remaining.decrementAndGet() != 0) return;
            if (failed.get()) {
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover incomplete; capture remains blocked");
                return;
            }
            MainHook.log("[DC][LauncherGlass] unlock endpoint rollover complete sessions=" + sessions.size());
            if (ready != null) ready.run();
        };

        for (LauncherGlassSession session : sessions) {
            try {
                HookUtil.invoke(session, "rebindProducer");
                Object value = HookUtil.getField(session, "renderHandler");
                if (!(value instanceof Handler)) throw new IllegalStateException("renderHandler unavailable");
                Handler renderHandler = (Handler) value;
                if (!renderHandler.post(() -> main.post(completeOne))) {
                    throw new IllegalStateException("render queue rejected unlock sentinel");
                }
            } catch (Throwable error) {
                failed.set(true);
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover failed: " + error);
                main.post(completeOne);
            }
        }
    }

    /**
     * HyperOS Workstation can keep the same valid root Surface across Recents while silently
     * retiring the PassBlur BufferQueue producer. The normal fresh-frame recovery therefore cannot
     * detect a Surface generation change. Roll every live shared producer before Recents uncovers
     * the scene; the controller will still keep the StaticLayer hidden until the new OES frame lands.
     */
    static synchronized void prepareWorkstationRecentsReturn() {
        if (!MainHook.isWorkstationMode()) return;
        int rebound = 0;
        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {
            if (session == null || session.isShutdown()) continue;
            HookUtil.invoke(session, "rebindProducer");
            rebound++;
        }
        MainHook.log("[DC][LauncherGlass] workstation Recents producer rollover sessions=" + rebound);
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
