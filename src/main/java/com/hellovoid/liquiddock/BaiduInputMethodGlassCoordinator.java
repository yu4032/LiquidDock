package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.WeakHashMap;

/** Owns glass sessions for structurally supported Baidu Input Method keyboard layouts. */
final class BaiduInputMethodGlassCoordinator {
    private static final String TAG = "[DC][BaiduInputMethodGlass]";
    private static final int MAX_GEOMETRY_FRAME_RETRIES = 24;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View authoritativeInputView;
        final BaiduInputMethodStructureResolver.Structure structure;
        final LiquidDockConfig.Glass glassConfig;
        final ViewGroup sinkHost;
        final View backgroundFrame;
        View root;
        float stockBackgroundAlpha = 1f;
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
                View authoritativeInputView,
                BaiduInputMethodStructureResolver.Structure structure,
                LiquidDockConfig.Glass glassConfig) {
            this.authoritativeInputView = authoritativeInputView;
            this.structure = structure;
            this.glassConfig = glassConfig;
            this.sinkHost = structure.sinkHost;
            this.backgroundFrame = structure.backgroundFrame;
        }
    }

    private BaiduInputMethodGlassCoordinator() {}

    static synchronized void onShown(
            View authoritativeInputView,
            BaiduInputMethodStructureResolver.Structure structure,
            LiquidDockConfig.Glass glassConfig) {
        if (authoritativeInputView == null || structure == null || glassConfig == null) return;
        State existing = STATES.get(authoritativeInputView);
        if (existing != null && !existing.released) {
            if (existing.session == null && authoritativeInputView.isAttachedToWindow()) {
                attachNow(existing);
            } else {
                syncGeometry(existing);
            }
            return;
        }

        State state = new State(authoritativeInputView, structure, glassConfig);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                attachNow(state);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                release(state);
            }
        };
        STATES.put(authoritativeInputView, state);
        authoritativeInputView.addOnAttachStateChangeListener(state.attachListener);
        if (authoritativeInputView.isAttachedToWindow()) attachNow(state);
    }

    static synchronized void onHidden(View authoritativeInputView) {
        if (authoritativeInputView == null) return;
        State state = STATES.remove(authoritativeInputView);
        if (state != null) release(state);
    }

    private static synchronized void attachNow(State state) {
        if (state == null || state.released || state.session != null
                || !state.authoritativeInputView.isAttachedToWindow()) return;
        View root = state.authoritativeInputView.getRootView();
        if (root == null || !root.isAttachedToWindow()) {
            failClosed(state, "IME root unavailable", null);
            return;
        }
        float radius = BaiduInputMethodStructureResolver.resolveCornerRadiusPx(state.structure);
        if (radius <= 0f) {
            failClosed(state, "keyboard radius unavailable from runtime outline", null);
            return;
        }

        state.root = root;
        state.cornerRadiusPx = radius;
        state.stockBackgroundAlpha = state.backgroundFrame.getAlpha();
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        state.sinkHost.addOnLayoutChangeListener(state.layoutListener);
        state.preDrawListener = () -> {
            syncGeometry(state);
            return true;
        };
        ViewTreeObserver observer = state.sinkHost.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(state.preDrawListener);

        BaiduInputMethodGlassSession session = new BaiduInputMethodGlassSession(
                root,
                state.glassConfig,
                new BaiduInputMethodGlassSession.Listener() {
                    @Override public void onPresented() {
                        state.authoritativeInputView.post(
                                () -> BaiduInputMethodGlassCoordinator.onPresented(state));
                    }

                    @Override public void onFailure(Throwable error) {
                        state.authoritativeInputView.post(
                                () -> failClosed(state, "session failure", error));
                    }
                });
        state.session = session;
        BaiduInputMethodGlassView sink = new BaiduInputMethodGlassView(
                state.sinkHost.getContext(), session);
        state.sink = sink;
        if (!insertSinkBelowContent(state, sink)) {
            failClosed(state, "unable to insert glass below keyboard content", null);
            return;
        }
        syncGeometry(state);
    }

    private static boolean insertSinkBelowContent(State state, BaiduInputMethodGlassView sink) {
        int contentIndex = state.sinkHost.indexOfChild(state.structure.contentView);
        if (contentIndex < 0) return false;
        try {
            state.sinkHost.addView(sink, contentIndex, new ViewGroup.LayoutParams(1, 1));
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
                state.backgroundFrame,
                state.cornerRadiusPx);
        if (next == null) {
            if (state.geometryRetryCount >= MAX_GEOMETRY_FRAME_RETRIES) {
                failClosed(state, "keyboard geometry never became valid", null);
                return;
            }
            if (!state.geometryRetryPosted) {
                state.geometryRetryPosted = true;
                state.geometryRetryCount++;
                state.sinkHost.postOnAnimation(() -> {
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
        View backgroundFrame = state.backgroundFrame;
        if (backgroundFrame == null || !backgroundFrame.isAttachedToWindow()) return;
        backgroundFrame.setAlpha(0f);
        state.stockHidden = true;
    }

    private static synchronized void failClosed(State state, String reason, Throwable error) {
        if (state == null || state.released) return;
        log(reason, error);
        release(state);
    }

    private static synchronized void release(State state) {
        if (state == null || state.released) return;
        state.released = true;
        if (STATES.get(state.authoritativeInputView) == state) {
            STATES.remove(state.authoritativeInputView);
        }
        restoreStockBackground(state);
        if (state.attachListener != null) {
            try {
                state.authoritativeInputView.removeOnAttachStateChangeListener(state.attachListener);
            } catch (Throwable ignored) {}
            state.attachListener = null;
        }
        if (state.layoutListener != null) {
            try { state.sinkHost.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }
        if (state.preDrawListener != null) {
            try {
                ViewTreeObserver observer = state.sinkHost.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(state.preDrawListener);
            } catch (Throwable ignored) {}
            state.preDrawListener = null;
        }
        BaiduInputMethodGlassView sink = state.sink;
        state.sink = null;
        if (sink != null) {
            try { sink.dispose(); } catch (Throwable ignored) {}
            try {
                if (sink.getParent() == state.sinkHost) state.sinkHost.removeView(sink);
            } catch (Throwable ignored) {}
        }
        BaiduInputMethodGlassSession session = state.session;
        state.session = null;
        if (session != null) {
            try { session.shutdown(); } catch (Throwable ignored) {}
        }
    }

    private static void restoreStockBackground(State state) {
        if (state == null || state.backgroundFrame == null) return;
        try { state.backgroundFrame.setAlpha(state.stockBackgroundAlpha); }
        catch (Throwable ignored) {}
        state.stockHidden = false;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
