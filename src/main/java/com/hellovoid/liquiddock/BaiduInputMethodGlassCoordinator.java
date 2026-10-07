package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.WeakHashMap;

/** Owns one glass session for a semantically identified Baidu floating-keyboard View. */
final class BaiduInputMethodGlassCoordinator {
    private static final String TAG = "[DC][BaiduInputMethodGlass]";
    private static final int MAX_GEOMETRY_FRAME_RETRIES = 24;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View authoritativeFloatView;
        final BaiduInputMethodStructureResolver.Structure structure;
        final LiquidDockConfig.Glass glassConfig;
        final ViewGroup sinkHost;
        final View glassTarget;
        final View stockBackgroundOwner;
        final Drawable stockBackgroundDrawable;
        View root;
        int stockBackgroundAlpha = 255;
        float cornerRadiusPx;
        BaiduInputMethodGlassSession session;
        BaiduInputMethodGlassView sink;
        View.OnAttachStateChangeListener attachListener;
        View.OnLayoutChangeListener layoutListener;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        boolean stockHidden;
        boolean captureRequested;
        boolean geometryRetryPosted;
        int geometryRetryCount;
        boolean released;

        State(
                View authoritativeFloatView,
                BaiduInputMethodStructureResolver.Structure structure,
                LiquidDockConfig.Glass glassConfig) {
            this.authoritativeFloatView = authoritativeFloatView;
            this.structure = structure;
            this.glassConfig = glassConfig;
            this.sinkHost = structure.sinkHost;
            this.glassTarget = structure.glassTarget;
            this.stockBackgroundOwner = structure.stockBackgroundOwner;
            Drawable stock = structure.stockBackgroundDrawable;
            this.stockBackgroundDrawable = stock != null ? stock.mutate() : null;
        }
    }

    private BaiduInputMethodGlassCoordinator() {}

    static synchronized void onShown(
            View authoritativeFloatView,
            BaiduInputMethodStructureResolver.Structure structure,
            LiquidDockConfig.Glass glassConfig) {
        if (authoritativeFloatView == null || structure == null || glassConfig == null) return;
        State existing = STATES.get(authoritativeFloatView);
        if (existing != null && !existing.released) {
            if (existing.glassTarget != structure.glassTarget
                    || existing.stockBackgroundOwner != structure.stockBackgroundOwner) {
                release(existing);
            } else {
                if (existing.session == null && authoritativeFloatView.isAttachedToWindow()) {
                    attachNow(existing);
                } else {
                    syncGeometry(existing);
                }
                return;
            }
        }

        State state = new State(authoritativeFloatView, structure, glassConfig);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                attachNow(state);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                release(state);
            }
        };
        STATES.put(authoritativeFloatView, state);
        authoritativeFloatView.addOnAttachStateChangeListener(state.attachListener);
        if (authoritativeFloatView.isAttachedToWindow()) attachNow(state);
    }

    static synchronized void onHidden(View authoritativeFloatView) {
        if (authoritativeFloatView == null) return;
        State state = STATES.remove(authoritativeFloatView);
        if (state != null) release(state);
    }

    private static synchronized void attachNow(State state) {
        if (state == null || state.released || state.session != null
                || !state.authoritativeFloatView.isAttachedToWindow()) return;
        View root = state.authoritativeFloatView.getRootView();
        if (root == null || !root.isAttachedToWindow()) {
            failClosed(state, "IME root unavailable", null);
            return;
        }
        if (state.stockBackgroundDrawable == null) {
            failClosed(state, "stock background drawable unavailable", null);
            return;
        }

        float radius = BaiduInputMethodStructureResolver.resolveCornerRadiusPx(state.structure);
        if (radius <= 0f) {
            float density = state.glassTarget.getResources().getDisplayMetrics().density;
            radius = 24f * Math.max(1f, density);
            log("runtime outline has no radius; using 24dp fallback", null);
        }

        state.root = root;
        state.cornerRadiusPx = radius;
        state.stockBackgroundAlpha = state.stockBackgroundDrawable.getAlpha();
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        state.glassTarget.addOnLayoutChangeListener(state.layoutListener);
        state.preDrawListener = () -> {
            syncGeometry(state);
            return true;
        };
        ViewTreeObserver observer = state.glassTarget.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(state.preDrawListener);

        BaiduInputMethodGlassSession session = new BaiduInputMethodGlassSession(
                root,
                state.glassConfig,
                new BaiduInputMethodGlassSession.Listener() {
                    @Override public void onPresented() {
                        state.authoritativeFloatView.post(
                                () -> BaiduInputMethodGlassCoordinator.onPresented(state));
                    }

                    @Override public void onFailure(Throwable error) {
                        state.authoritativeFloatView.post(
                                () -> failClosed(state, "session failure", error));
                    }
                });
        state.session = session;
        BaiduInputMethodGlassView sink = new BaiduInputMethodGlassView(
                state.sinkHost.getContext(), session);
        state.sink = sink;
        if (!insertSinkBelowTarget(state, sink)) {
            failClosed(state, "unable to insert glass below floating keyboard shell", null);
            return;
        }
        log("glass session attached " + BaiduInputMethodStructureResolver.describe(state.structure),
                null);
        syncGeometry(state);
    }

    private static boolean insertSinkBelowTarget(State state, BaiduInputMethodGlassView sink) {
        int targetIndex = state.sinkHost.indexOfChild(state.glassTarget);
        if (targetIndex < 0) return false;
        try {
            state.sinkHost.addView(sink, targetIndex, new ViewGroup.LayoutParams(1, 1));
            return true;
        } catch (Throwable error) {
            log("glass insertion failed", error);
            return false;
        }
    }

    private static synchronized void syncGeometry(State state) {
        if (state == null || state.released || state.session == null || state.root == null) return;
        BaiduInputMethodGlassGeometry next = BaiduInputMethodGlassGeometry.capture(
                state.root,
                state.sinkHost,
                state.glassTarget,
                state.cornerRadiusPx);
        if (next == null) {
            if (state.geometryRetryCount >= MAX_GEOMETRY_FRAME_RETRIES) {
                failClosed(state, "floating keyboard geometry never became valid", null);
                return;
            }
            if (!state.geometryRetryPosted) {
                state.geometryRetryPosted = true;
                state.geometryRetryCount++;
                state.glassTarget.postOnAnimation(() -> {
                    synchronized (BaiduInputMethodGlassCoordinator.class) {
                        state.geometryRetryPosted = false;
                        if (state.released) return;
                    }
                    syncGeometry(state);
                });
            }
            return;
        }
        state.geometryRetryCount = 0;
        syncSinkBounds(state, next);
        state.session.updateGeometry(next);
        if (!state.captureRequested) {
            state.captureRequested = true;
            state.session.requestInitialCapture();
        }
    }

    private static void syncSinkBounds(State state, BaiduInputMethodGlassGeometry geometry) {
        if (state == null || state.sink == null || geometry == null) return;
        int width = geometry.sinkWidthPx();
        int height = geometry.sinkHeightPx();
        ViewGroup.LayoutParams params = state.sink.getLayoutParams();
        if (params == null) return;
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            state.sink.setLayoutParams(params);
        }
        if (state.sink.getX() != geometry.sinkLeft) state.sink.setX(geometry.sinkLeft);
        if (state.sink.getY() != geometry.sinkTop) state.sink.setY(geometry.sinkTop);
    }

    private static synchronized void onPresented(State state) {
        if (state == null || state.released || state.stockHidden) return;
        if (state.stockBackgroundOwner == null || state.stockBackgroundDrawable == null
                || !state.stockBackgroundOwner.isAttachedToWindow()) return;
        try {
            state.stockBackgroundDrawable.setAlpha(0);
            state.stockBackgroundOwner.invalidate();
            state.stockHidden = true;
            log("first TextureView frame presented; stock background drawable hidden", null);
        } catch (Throwable error) {
            failClosed(state, "unable to hide stock background drawable", error);
        }
    }

    private static synchronized void failClosed(State state, String reason, Throwable error) {
        if (state == null || state.released) return;
        log(reason, error);
        release(state);
    }

    private static synchronized void release(State state) {
        if (state == null || state.released) return;
        state.released = true;
        if (STATES.get(state.authoritativeFloatView) == state) {
            STATES.remove(state.authoritativeFloatView);
        }
        restoreStockBackground(state);
        if (state.attachListener != null) {
            try {
                state.authoritativeFloatView.removeOnAttachStateChangeListener(state.attachListener);
            } catch (Throwable ignored) {}
            state.attachListener = null;
        }
        if (state.layoutListener != null) {
            try { state.glassTarget.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }
        if (state.preDrawListener != null) {
            try {
                ViewTreeObserver observer = state.glassTarget.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(state.preDrawListener);
            } catch (Throwable ignored) {}
            state.preDrawListener = null;
        }
        BaiduInputMethodGlassView sink = state.sink;
        state.sink = null;
        if (sink != null) {
            try { sink.setVisibility(View.INVISIBLE); } catch (Throwable ignored) {}
            try { sink.dispose(); } catch (Throwable ignored) {}
            removeSinkDeferred(state.sinkHost, sink);
        }
        BaiduInputMethodGlassSession session = state.session;
        state.session = null;
        if (session != null) {
            try { session.shutdown(); } catch (Throwable ignored) {}
        }
        log("glass session released", null);
    }

    /**
     * Never mutate the host's child list synchronously from an attach-state callback.
     *
     * <p>ViewGroup dispatches detach by iterating its current children. Removing the injected
     * TextureView from onViewDetachedFromWindow() can shift that array mid-dispatch and crash the
     * host with ViewGroup.dispatchDetachedFromWindow() dereferencing a null child. Hide/dispose
     * immediately, then remove on the next main-loop turn after the framework traversal completes.
     */
    private static void removeSinkDeferred(ViewGroup host, BaiduInputMethodGlassView sink) {
        if (host == null || sink == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (sink.getParent() == host) host.removeView(sink);
                } catch (Throwable error) {
                    log("deferred glass removal failed", error);
                }
            });
        } catch (Throwable error) {
            // Fail closed without falling back to a synchronous remove during detach dispatch.
            log("unable to schedule deferred glass removal", error);
        }
    }

    private static void restoreStockBackground(State state) {
        if (state == null || state.stockBackgroundDrawable == null) return;
        try {
            state.stockBackgroundDrawable.setAlpha(state.stockBackgroundAlpha);
            if (state.stockBackgroundOwner != null) state.stockBackgroundOwner.invalidate();
        } catch (Throwable ignored) {}
        state.stockHidden = false;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
