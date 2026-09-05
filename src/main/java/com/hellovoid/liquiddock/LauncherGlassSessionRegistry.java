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

    interface WorkstationRecoveryCompletion {
        void onComplete(LauncherGlassProducerRecoveryState.Result result);
    }

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
        Runnable completeOne = () -> {
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
                    failed.set(true);
                    MainHook.log("[DC][LauncherGlass] unlock endpoint rollover rejected by render queue");
                    main.post(completeOne);
                }
            } catch (Throwable error) {
                failed.set(true);
                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover failed: " + error);
                main.post(completeOne);
            }
        }
    }

    /**
     * Recover every live shared Launcher producer for one authoritative Workstation Recents return.
     * Completion is terminal: ACCEPTED means endpoint recreated, PassBlur bind succeeded and a
     * fresh frame from that endpoint generation arrived. Queue acceptance is never aggregate
     * success. REJECTED/FAILED therefore keep HOME fail-closed in the Recents adapter.
     */
    static void prepareWorkstationRecentsReturn(
            long recoverySerial, WorkstationRecoveryCompletion completion) {
        ArrayList<LauncherGlassSession> sessions;
        synchronized (LauncherGlassSessionRegistry.class) {
            sessions = new ArrayList<>(SESSIONS.values());
        }
        sessions.removeIf(session -> session == null || session.isShutdown());

        WorkstationProducerRecoveryAggregate aggregate =
                new WorkstationProducerRecoveryAggregate(sessions.size());
        AtomicBoolean completionDelivered = new AtomicBoolean(false);

        if (aggregate.isComplete()) {
            logWorkstationAggregate(recoverySerial, aggregate);
            if (completion != null) completion.onComplete(aggregate.terminalResult());
            return;
        }

        for (LauncherGlassSession session : sessions) {
            LauncherGlassProducerRecoveryState.Result requestResult;
            try {
                requestResult = session.rebindWorkstationProducer(
                        "workstation-recents", recoverySerial,
                        terminal -> recordWorkstationTerminal(
                                recoverySerial, aggregate, completionDelivered,
                                completion, terminal));
            } catch (Throwable error) {
                requestResult = LauncherGlassProducerRecoveryState.Result.FAILED;
                MainHook.log("[DC][LauncherGlass][ProducerRecovery] reason=workstation-recents"
                        + " session=" + session.diagnosticSessionId()
                        + " producerGeneration=-1 recoverySerial=" + recoverySerial
                        + " result=FAILED stage=request endpointRecreated=false"
                        + " bindSucceeded=false freshFrameArrived=false error=" + error);
            }

            if (requestResult != LauncherGlassProducerRecoveryState.Result.ACCEPTED) {
                recordWorkstationTerminal(
                        recoverySerial, aggregate, completionDelivered,
                        completion, requestResult);
            }
        }
    }

    private static void recordWorkstationTerminal(
            long recoverySerial,
            WorkstationProducerRecoveryAggregate aggregate,
            AtomicBoolean completionDelivered,
            WorkstationRecoveryCompletion completion,
            LauncherGlassProducerRecoveryState.Result terminal) {
        if (terminal == null || !aggregate.record(terminal)) return;
        logWorkstationAggregate(recoverySerial, aggregate);
        if (!completionDelivered.compareAndSet(false, true)) return;
        if (completion != null) completion.onComplete(aggregate.terminalResult());
    }

    private static void logWorkstationAggregate(
            long recoverySerial, WorkstationProducerRecoveryAggregate aggregate) {
        MainHook.log("[DC][LauncherGlass][ProducerRecovery] reason=workstation-recents"
                + " session=aggregate producerGeneration=-1 recoverySerial=" + recoverySerial
                + " result=" + aggregate.terminalResult()
                + " stage=terminal endpointRecreated=n/a bindSucceeded=n/a freshFrameArrived=n/a"
                + " accepted=" + aggregate.acceptedCount()
                + " rejected=" + aggregate.rejectedCount()
                + " failed=" + aggregate.failedCount()
                + " total=" + aggregate.expectedCount()
                + " aggregateTerminal=" + aggregate.terminalResult());
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
