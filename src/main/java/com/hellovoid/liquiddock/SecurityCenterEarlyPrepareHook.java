package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Defers Security Center material binding until the configured panel views are actually ready. */
final class SecurityCenterEarlyPrepareHook {
    private static final Object LOCK = new Object();
    private static final Set<Method> INSTALLED = new HashSet<>();
    private static final Map<View, PendingPrepare> PENDING = new WeakHashMap<>();
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    private SecurityCenterEarlyPrepareHook() {}

    static void install(
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (contract == null || videoMainContentResId == 0) return;
        Method configure = contract.configure();
        synchronized (LOCK) {
            if (!INSTALLED.add(configure)) return;
        }
        HookUtil.hook(configure, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            Object turboObject = chain.getThisObject();
            Object typeArg = chain.getArgs().size() > 4 ? chain.getArgs().get(4) : null;
            if (!(turboObject instanceof View) || typeArg == null) return result;
            try {
                int type = assistantType(typeArg, contract);
                if (type == 0) return result;
                armPendingPrepare(
                        (View) turboObject, type, contract, videoMainContentResId);
            } catch (Throwable error) {
                log("deferred prepare setup failed closed", error);
            }
            return result;
        });
    }

    private static void armPendingPrepare(
            View turbo,
            int type,
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        PendingPrepare next = new PendingPrepare(turbo, type, contract, videoMainContentResId);
        PendingPrepare previous;
        synchronized (LOCK) {
            previous = PENDING.put(turbo, next);
        }
        if (previous != null) previous.dispose();
        next.arm();
    }

    private static boolean tryBindWhenReady(PendingPrepare pending) {
        View turbo = pending.turboRef.get();
        if (turbo == null || !SecurityCenterGlassRuntimeState.isEnabled()) return false;
        try {
            Object dockObject = invoke(pending.contract.dockGetter(), turbo);
            if (!(dockObject instanceof View)) return false;
            View dock = (View) dockObject;

            View boxMaterial = resolveBoxMaterial(
                    turbo,
                    pending.type,
                    pending.contract,
                    pending.videoMainContentResId);
            if ((pending.type == ASSISTANT_GAME || pending.type == ASSISTANT_VIDEO)
                    && boxMaterial == null) {
                return false;
            }
            SecurityCenterGlassRuntimeState.bindAssistant(
                    turbo, dock, boxMaterial, pending.type);
            log("deferred prepare bound type=" + pending.type, null);
            return true;
        } catch (Throwable error) {
            log("deferred prepare not ready", error);
            return false;
        }
    }

    private static int assistantType(
            Object typeArg, SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        Object value = invoke(contract.assistantTypeDiscriminator(), typeArg);
        if (!(value instanceof Number)) return 0;
        int type = ((Number) value).intValue();
        return type == ASSISTANT_GAME || type == ASSISTANT_VIDEO || type == ASSISTANT_GLOBAL_DOCK
                ? type : 0;
    }

    private static View resolveBoxMaterial(
            View turbo,
            int type,
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (type == ASSISTANT_GLOBAL_DOCK) return null;
        Object boxObject = invoke(contract.boxGetter(), turbo);
        if (!(boxObject instanceof View)) return null;
        View box = (View) boxObject;
        if (type == ASSISTANT_GAME) {
            if (!contract.gameBoxClass().isInstance(box)) return null;
            Object material = invoke(contract.gameMaterialGetter(), box);
            return material instanceof View && contract.gameMaterialClass().isInstance(material)
                    ? (View) material : null;
        }
        View material = box.findViewById(videoMainContentResId);
        return material != null && material.getId() == videoMainContentResId ? material : null;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Security Center deferred prepare invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center deferred prepare invocation failed", error);
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) {
                Api101Bridge.log("[DC][SecurityCenterGlass] " + message + ": " + error);
            } else {
                Api101Bridge.log("[DC][SecurityCenterGlass] " + message);
            }
        } catch (Throwable ignored) {}
    }

    private static final class PendingPrepare
            implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> turboRef;
        final int type;
        final SecurityCenterSemanticContractResolver.ResolvedContract contract;
        final int videoMainContentResId;
        private ViewTreeObserver observedTree;
        private boolean disposed;

        PendingPrepare(
                View turbo,
                int type,
                SecurityCenterSemanticContractResolver.ResolvedContract contract,
                int videoMainContentResId) {
            this.turboRef = new WeakReference<>(turbo);
            this.type = type;
            this.contract = contract;
            this.videoMainContentResId = videoMainContentResId;
        }

        void arm() {
            View turbo = turboRef.get();
            if (turbo == null || disposed) {
                dispose();
                return;
            }
            turbo.addOnAttachStateChangeListener(this);
            if (tryBindWhenReady(this)) {
                dispose();
                return;
            }
            armPreDrawIfAttached(turbo);
        }

        @Override
        public boolean onPreDraw() {
            if (disposed) return true;
            View turbo = turboRef.get();
            if (turbo == null) {
                dispose();
                return true;
            }
            if (tryBindWhenReady(this)) {
                dispose();
            } else {
                armPreDrawIfAttached(turbo);
            }
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            if (disposed || view != turboRef.get()) return;
            if (tryBindWhenReady(this)) {
                dispose();
                return;
            }
            armPreDrawIfAttached(view);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (view == turboRef.get()) dispose();
        }

        private void armPreDrawIfAttached(View turbo) {
            if (disposed || !turbo.isAttachedToWindow()) return;
            ViewTreeObserver nextTree = turbo.getViewTreeObserver();
            if (nextTree == null || !nextTree.isAlive() || nextTree == observedTree) return;
            ViewTreeObserver previousTree = observedTree;
            observedTree = nextTree;
            if (previousTree != null && previousTree.isAlive()) {
                previousTree.removeOnPreDrawListener(this);
            }
            nextTree.addOnPreDrawListener(this);
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            View turbo = turboRef.get();
            if (turbo != null) turbo.removeOnAttachStateChangeListener(this);
            ViewTreeObserver tree = observedTree;
            observedTree = null;
            if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
            synchronized (LOCK) {
                if (turbo != null && PENDING.get(turbo) == this) PENDING.remove(turbo);
            }
        }
    }
}
