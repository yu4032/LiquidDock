package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import java.util.WeakHashMap;

/** Owns zero-copy Prismal output below one Gboard companion toolbar's controls. */
final class GboardHandwritingCapsuleGlassCoordinator {
    private static final String TAG = "[DC][GboardToolbarGlass]";
    private static final WeakHashMap<ViewGroup, State> STATES = new WeakHashMap<>();

    private static final class State {
        final ViewGroup host;
        final LiquidDockConfig.Glass glassConfig;
        View root;
        float cornerRadiusPx;
        GboardFloatingGlassSession session;
        GboardFloatingGlassView sink;
        View.OnAttachStateChangeListener attachListener;
        View.OnLayoutChangeListener layoutListener;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        boolean captureRequested;
        boolean released;

        State(ViewGroup host, LiquidDockConfig.Glass glassConfig) {
            this.host = host;
            this.glassConfig = glassConfig;
        }
    }

    private GboardHandwritingCapsuleGlassCoordinator() {}

    static synchronized void onShown(ViewGroup host, LiquidDockConfig.Glass glassConfig) {
        if (host == null || glassConfig == null) return;
        State existing = STATES.get(host);
        if (existing != null && !existing.released) {
            if (existing.session == null && host.isAttachedToWindow()) attachNow(existing);
            else syncGeometry(existing);
            return;
        }

        State state = new State(host, glassConfig);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                attachNow(state);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                release(state);
            }
        };
        STATES.put(host, state);
        host.addOnAttachStateChangeListener(state.attachListener);
        if (host.isAttachedToWindow()) attachNow(state);
    }

    static synchronized void onHidden(ViewGroup host) {
        if (host == null) return;
        State state = STATES.remove(host);
        if (state != null) release(state);
    }

    private static synchronized void attachNow(State state) {
        if (state == null || state.released || state.session != null
                || !state.host.isAttachedToWindow()) return;
        View root = state.host.getRootView();
        if (root == null || !root.isAttachedToWindow()) {
            failClosed(state, "toolbar root unavailable", null);
            return;
        }
        float cornerRadiusPx = resolveCornerRadiusPx(state.host);
        if (cornerRadiusPx <= 0f) {
            failClosed(state, "toolbar radius unavailable", null);
            return;
        }

        state.root = root;
        state.cornerRadiusPx = cornerRadiusPx;
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        state.host.addOnLayoutChangeListener(state.layoutListener);
        state.preDrawListener = () -> {
            syncGeometry(state);
            return true;
        };
        ViewTreeObserver observer = state.host.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(state.preDrawListener);

        GboardFloatingGlassSession session = new GboardFloatingGlassSession(
                root,
                state.glassConfig,
                new GboardFloatingGlassSession.Listener() {
                    @Override public void onPresented() {
                        // Nothing to hide: the vendor material remains behind the transparent sink.
                    }

                    @Override public void onFailure(Throwable error) {
                        state.host.post(() -> failClosed(state, "session failure", error));
                    }
                });
        state.session = session;
        GboardFloatingGlassView sink = new GboardFloatingGlassView(
                state.host.getContext(), session);
        state.sink = sink;
        try {
            // The stock material is drawn by ShadowedSoftKeyboardView itself. Child index 0
            // overlays only that material; all existing Gboard controls remain above Prismal.
            state.host.addView(sink, 0, new ViewGroup.LayoutParams(1, 1));
        } catch (Throwable error) {
            failClosed(state, "unable to insert toolbar glass below controls", error);
            return;
        }
        syncGeometry(state);
    }

    private static synchronized void syncGeometry(State state) {
        if (state == null || state.released || state.session == null
                || state.sink == null || state.root == null) return;
        GboardFloatingGlassGeometry geometry = GboardFloatingGlassGeometry.captureTarget(
                state.root, state.host, state.host, state.cornerRadiusPx);
        if (geometry == null) return; // stock remains visible; next pre-draw may become valid.

        int width = geometry.sinkWidthPx();
        int height = geometry.sinkHeightPx();
        ViewGroup.LayoutParams params = state.sink.getLayoutParams();
        if (params != null && (params.width != width || params.height != height)) {
            params.width = width;
            params.height = height;
            state.sink.setLayoutParams(params);
        }
        if (state.sink.getX() != geometry.sinkLeft) state.sink.setX(geometry.sinkLeft);
        if (state.sink.getY() != geometry.sinkTop) state.sink.setY(geometry.sinkTop);
        state.session.updateGeometry(geometry);
        if (!state.captureRequested) {
            state.captureRequested = true;
            state.session.requestInitialCapture();
        }
    }

    private static float resolveCornerRadiusPx(View view) {
        float radius = outlineRadius(view);
        if (radius > 0f) return radius;
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return 0f;
        return Math.min(view.getWidth(), view.getHeight()) * 0.5f;
    }

    private static float outlineRadius(View view) {
        if (view == null) return 0f;
        try {
            Outline outline = new Outline();
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider != null) {
                provider.getOutline(view, outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
        } catch (Throwable ignored) {}
        try {
            Drawable background = view.getBackground();
            if (background != null) {
                Outline outline = new Outline();
                background.getOutline(outline);
                if (outline.getRadius() > 0f) return outline.getRadius();
            }
        } catch (Throwable ignored) {}
        return 0f;
    }

    private static synchronized void failClosed(State state, String reason, Throwable error) {
        if (state == null || state.released) return;
        log(reason, error);
        release(state);
    }

    private static synchronized void release(State state) {
        if (state == null || state.released) return;
        state.released = true;
        if (STATES.get(state.host) == state) STATES.remove(state.host);

        if (state.attachListener != null) {
            try { state.host.removeOnAttachStateChangeListener(state.attachListener); }
            catch (Throwable ignored) {}
            state.attachListener = null;
        }
        if (state.layoutListener != null) {
            try { state.host.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }
        if (state.preDrawListener != null) {
            try {
                ViewTreeObserver observer = state.host.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(state.preDrawListener);
            } catch (Throwable ignored) {}
            state.preDrawListener = null;
        }

        GboardFloatingGlassView sink = state.sink;
        state.sink = null;
        if (sink != null) {
            try { sink.dispose(); } catch (Throwable ignored) {}
        }
        GboardFloatingGlassSession session = state.session;
        state.session = null;
        if (session != null) {
            try { session.shutdown(); } catch (Throwable ignored) {}
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
