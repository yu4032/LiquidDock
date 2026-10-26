package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Retains native PassBlur bindings and their owner continuations until SurfaceFlinger
 * confirms Transaction commit. No owner may release the producer while still pending.
 *
 * Unrecoverable detach failure intentionally retains the owner (fail-closed) rather
 * than releasing a Surface that may still be sampled by the compositor. This ledger is
 * process-scoped and does NOT make the process safe for hot reload.
 */
final class PassBlurDetachCoordinator {
    private static final String TAG = "[DC][PassBlurRetire]";
    private static final int AUTOMATIC_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 80L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Miuix307PassBlurBridge.Binding, Pending> PENDING =
            new IdentityHashMap<>();

    private PassBlurDetachCoordinator() {}

    /** Accepts a request, not a guarantee of completion. Completion runs on main. */
    static void retire(Miuix307PassBlurBridge.Binding binding, Runnable onCommitted) {
        if (binding == null) {
            postMain(onCommitted);
            return;
        }
        Pending pending;
        synchronized (PENDING) {
            pending = PENDING.get(binding);
            if (pending == null) {
                pending = new Pending(binding);
                PENDING.put(binding, pending);
            }
            if (onCommitted != null) pending.addContinuation(onCommitted);
        }
        pending.schedule();
    }

    static int pendingCount() {
        synchronized (PENDING) {
            return PENDING.size();
        }
    }

    static void retryPending() {
        ArrayList<Pending> snapshot;
        synchronized (PENDING) {
            snapshot = new ArrayList<>(PENDING.values());
        }
        for (Pending pending : snapshot) pending.schedule();
    }

    private static boolean postMain(Runnable action) {
        if (action == null) return true;
        try {
            if (Looper.myLooper() == MAIN.getLooper()) {
                action.run();
                return true;
            }
            return MAIN.post(action);
        } catch (Throwable error) {
            log("main handler dispatch failed: " + error);
            return false;
        }
    }

    private static void log(String message) {
        try { MainHook.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }

    private static final class Pending {
        final Miuix307PassBlurBridge.Binding binding;
        final ArrayList<Runnable> continuations = new ArrayList<>();
        final Runnable committedCallback = this::onCommitted;
        boolean completed;
        boolean finishQueued;
        boolean attemptQueued;
        int attempts;

        Pending(Miuix307PassBlurBridge.Binding binding) {
            this.binding = binding;
        }

        void addContinuation(Runnable callback) {
            synchronized (this) {
                if (!completed && !continuations.contains(callback)) continuations.add(callback);
            }
        }

        void schedule() {
            synchronized (this) {
                if (completed || attemptQueued || finishQueued) return;
                attemptQueued = true;
            }
            if (!postMain(this::attempt)) {
                synchronized (this) { attemptQueued = false; }
                log("detach scheduling rejected root=" + binding.rootName);
            }
        }

        void attempt() {
            synchronized (this) {
                attemptQueued = false;
                if (completed || finishQueued) return;
                attempts++;
            }
            boolean detached = Miuix307PassBlurBridge.tryUnbind(binding, committedCallback);
            if (detached) {
                onCommitted();
                return;
            }
            synchronized (this) {
                if (completed || finishQueued) return;
                if (attempts >= AUTOMATIC_ATTEMPTS) {
                    log("detach unconfirmed after " + attempts + " attempts; retained root="
                            + binding.rootName);
                    return;
                }
                attemptQueued = true;
            }
            try {
                if (!MAIN.postDelayed(this::attempt, RETRY_DELAY_MS)) {
                    synchronized (this) { attemptQueued = false; }
                    log("detach retry scheduling rejected root=" + binding.rootName);
                }
            } catch (Throwable error) {
                synchronized (this) { attemptQueued = false; }
                log("detach retry scheduling failed: " + error);
            }
        }

        void onCommitted() {
            synchronized (this) {
                if (completed || finishQueued) return;
                finishQueued = true;
            }
            if (!postMain(this::finish)) {
                synchronized (this) { finishQueued = false; }
                log("commit received but owner continuation scheduling failed root="
                        + binding.rootName);
            }
        }

        void finish() {
            ArrayList<Runnable> callbacks;
            synchronized (this) {
                if (completed) return;
                // Do not allow a spurious or early callback to release resources.
                if (binding.bound) {
                    finishQueued = false;
                    log("callback arrived before confirmed native detach root=" + binding.rootName);
                    return;
                }
                completed = true;
                callbacks = new ArrayList<>(continuations);
                continuations.clear();
            }
            synchronized (PENDING) {
                if (PENDING.get(binding) == this) PENDING.remove(binding);
            }
            PassBlurDetachCallbacks.dispatch(callbacks,
                    error -> log("owner continuation failed: " + error));
        }
    }
}
