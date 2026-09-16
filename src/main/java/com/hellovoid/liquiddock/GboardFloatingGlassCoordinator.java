package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import java.util.WeakHashMap;

/** Owns one structurally identified Gboard floating-keyboard glass session. */
final class GboardFloatingGlassCoordinator {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final int MAX_INVALID_GEOMETRY_FRAMES = 24;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View popup;
        final GboardFloatingStructureResolver.Structure structure;
        final LiquidDockConfig.Glass glassConfig;
        final ViewGroup keyboardArea;
        final GboardFloatingOutputSelection outputSelection = new GboardFloatingOutputSelection();
        ViewGroup sinkHost;
        View backgroundFrame;
        View root;
        float stockBackgroundAlpha = 1f;
        float cornerRadiusPx;
        GboardFloatingGlassSession session;
        GboardFloatingGlassOutput output;
        GboardFloatingGlassView sink;
        View.OnAttachStateChangeListener attachListener;
        Choreographer.FrameCallback frameCallback;
        GboardFloatingHandlePolicy.DragObserver dragObserver;
        boolean frameTracking;
        boolean stockHidden;
        boolean captureRequested;
        int invalidGeometryFrames;
        boolean released;

        State(View popup, GboardFloatingStructureResolver.Structure structure,
                LiquidDockConfig.Glass glassConfig) {
            this.popup = popup;
            this.structure = structure;
            this.glassConfig = glassConfig;
            this.keyboardArea = structure.keyboardArea;
            this.backgroundFrame = structure.stockBackground;
        }
    }

    private GboardFloatingGlassCoordinator() {}

    static synchronized void onShown(View popup,
            GboardFloatingStructureResolver.Structure structure,
            LiquidDockConfig.Glass glassConfig) {
        if (popup == null || structure == null || glassConfig == null) return;
        State existing = STATES.get(popup);
        if (existing != null && !existing.released) {
            if (existing.session == null && popup.isAttachedToWindow()) attachNow(existing);
            else startFrameTracking(existing);
            return;
        }
        State state = new State(popup, structure, glassConfig);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { attachNow(state); }
            @Override public void onViewDetachedFromWindow(View view) { release(state); }
        };
        STATES.put(popup, state);
        popup.addOnAttachStateChangeListener(state.attachListener);
        if (popup.isAttachedToWindow()) attachNow(state);
    }

    static synchronized void onHidden(View popup) {
        if (popup == null) return;
        State state = STATES.remove(popup);
        if (state != null) release(state);
    }

    private static synchronized void attachNow(State state) {
        if (state == null || state.released || state.session != null
                || !state.popup.isAttachedToWindow()) return;
        View root = state.popup.getRootView();
        if (root == null || !root.isAttachedToWindow()) {
            failClosed(state, "popup root unavailable", null);
            return;
        }
        float cornerRadiusPx = resolveCornerRadiusPx(state.structure);
        if (cornerRadiusPx <= 0f) {
            failClosed(state, "floating radius unavailable from runtime outline", null);
            return;
        }

        state.root = root;
        state.cornerRadiusPx = cornerRadiusPx;
        state.stockBackgroundAlpha = state.backgroundFrame.getAlpha();

        GboardFloatingGlassSession session = new GboardFloatingGlassSession(
                root, state.glassConfig, new GboardFloatingGlassSession.Listener() {
                    @Override public void onFirstSwap() {
                        state.popup.post(() -> GboardFloatingGlassCoordinator.onFirstSwap(state));
                    }
                    @Override public void onPresented() {
                        state.popup.post(() -> GboardFloatingGlassCoordinator.onPresented(state));
                    }
                    @Override public void onFailure(Throwable error) {
                        state.popup.post(() -> failClosed(state, "session failure", error));
                    }
                });
        state.session = session;
        state.dragObserver = new GboardFloatingHandlePolicy.DragObserver() {
            @Override public void onDragStarted() {
                GboardFloatingGlassSession live = state.session;
                if (!state.released && live != null) live.beginDragSnapshot();
            }
            @Override public void onDragEnded() {
                GboardFloatingGlassSession live = state.session;
                if (!state.released && live != null) live.endDragSnapshot();
            }
        };
        GboardFloatingHandlePolicy.observe(state.structure.bottomFrame, state.dragObserver);

        GboardFloatingGlassOutput.Listener outputListener = outputListener(state);
        GboardSurfaceControlGlassOutput preferred =
                GboardSurfaceControlGlassOutput.create(root, outputListener);
        if (preferred != null) {
            state.outputSelection.onSurfaceControlReady();
            state.output = preferred;
            state.sinkHost = state.keyboardArea;
            log("using SurfaceControl output", null);
        } else {
            state.outputSelection.onSurfaceControlFailed(true);
            GboardTextureViewGlassOutput fallback = GboardTextureViewGlassOutput.create(
                    state.keyboardArea.getContext(), outputListener);
            if (fallback == null) {
                state.outputSelection.onSurfaceControlFailed(false);
                failClosed(state, "unable to create any glass output", null);
                return;
            }
            state.output = fallback;
            state.sink = fallback.view();
            if (!insertSinkBelowKeyboardContent(state, state.sink)) {
                state.outputSelection.onSurfaceControlFailed(false);
                failClosed(state, "unable to insert TextureView fallback", null);
                return;
            }
            log("using TextureView fallback output", null);
        }

        state.frameCallback = new Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) {
                synchronized (GboardFloatingGlassCoordinator.class) {
                    if (state.released || !state.frameTracking) return;
                }
                syncAuthoritativeFrame(state);
                synchronized (GboardFloatingGlassCoordinator.class) {
                    if (state.released || !state.frameTracking) return;
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };
        startFrameTracking(state);
    }

    private static GboardFloatingGlassOutput.Listener outputListener(State state) {
        return new GboardFloatingGlassOutput.Listener() {
            @Override public void onSurfaceReady(Surface surface, int width, int height) {
                GboardFloatingGlassSession live = state.session;
                if (state.released || live == null) {
                    surface.release();
                    return;
                }
                live.attachOutput(surface, width, height);
            }

            @Override public void onSurfaceSizeChanged(int width, int height) {
                GboardFloatingGlassSession live = state.session;
                if (!state.released && live != null) live.resizeOutput(width, height);
            }

            @Override public void onPresented() {
                GboardFloatingGlassSession live = state.session;
                if (!state.released && live != null) live.onOutputPresented();
            }

            @Override public void onFailed(String reason, Throwable error) {
                state.popup.post(() -> failClosed(state, "output failure: " + reason, error));
            }
        };
    }

    private static synchronized void onFirstSwap(State state) {
        if (state == null || state.released) return;
        GboardFloatingGlassOutput output = state.output;
        if (output != null) output.showAfterFirstSwap();
    }

    private static synchronized void startFrameTracking(State state) {
        if (state == null || state.released || state.frameTracking || state.frameCallback == null) return;
        state.frameTracking = true;
        Choreographer.getInstance().removeFrameCallback(state.frameCallback);
        Choreographer.getInstance().postFrameCallback(state.frameCallback);
    }

    private static boolean insertSinkBelowKeyboardContent(State state, GboardFloatingGlassView sink) {
        if (state == null || sink == null) return false;
        ViewGroup host = state.keyboardArea;
        int contentIndex = host.indexOfChild(state.structure.contentColumn);
        if (contentIndex < 0) return false;
        try {
            host.addView(sink, contentIndex, new ViewGroup.LayoutParams(1, 1));
            state.sinkHost = host;
            return true;
        } catch (Throwable error) {
            log("glass insertion failed", error);
            return false;
        }
    }

    private static float resolveCornerRadiusPx(GboardFloatingStructureResolver.Structure structure) {
        if (structure == null) return 0f;
        float radius = outlineRadius(structure.stockBackground);
        if (radius > 0f) return radius;
        radius = outlineRadius(structure.keyboardArea);
        return radius > 0f ? radius : 0f;
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

    private static void syncAuthoritativeFrame(State state) {
        if (state == null || state.released || state.session == null
                || state.sinkHost == null || state.root == null) return;
        GboardFloatingGlassGeometry next = GboardFloatingGlassGeometry.capture(
                state.root, state.sinkHost, state.structure, state.cornerRadiusPx);
        if (next == null) {
            state.invalidGeometryFrames++;
            if (state.invalidGeometryFrames >= MAX_INVALID_GEOMETRY_FRAMES) {
                failClosed(state, "floating geometry never became valid", null);
            }
            return;
        }
        state.invalidGeometryFrames = 0;
        syncSinkBounds(state, next);
        state.session.updateGeometry(next);
        GboardFloatingGlassOutput output = state.output;
        if (output != null) output.updateGeometry(next);
        if (!state.captureRequested) {
            state.captureRequested = true;
            state.session.requestInitialCapture();
        }
    }

    private static void syncSinkBounds(State state, GboardFloatingGlassGeometry geometry) {
        if (state == null || geometry == null || state.sinkHost == null || state.sink == null) return;
        int width = geometry.sinkWidthPx();
        int height = geometry.sinkHeightPx();
        if (width <= 0 || height <= 0) return;
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
        if (!GboardStockVisualAuthority.claim(state.structure)) {
            failClosed(state, "unable to claim floating stock visuals", null);
            return;
        }
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
        if (STATES.get(state.popup) == state) STATES.remove(state.popup);
        state.frameTracking = false;
        if (state.frameCallback != null) {
            try { Choreographer.getInstance().removeFrameCallback(state.frameCallback); }
            catch (Throwable ignored) {}
            state.frameCallback = null;
        }
        if (state.dragObserver != null) {
            try { GboardFloatingHandlePolicy.clearObserver(state.structure.bottomFrame, state.dragObserver); }
            catch (Throwable ignored) {}
            state.dragObserver = null;
        }
        GboardStockVisualAuthority.release(state.structure);
        restoreStockBackground(state);
        if (state.attachListener != null) {
            try { state.popup.removeOnAttachStateChangeListener(state.attachListener); }
            catch (Throwable ignored) {}
            state.attachListener = null;
        }
        GboardFloatingGlassSession session = state.session;
        state.session = null;
        if (session != null) {
            try { session.shutdown(); } catch (Throwable ignored) {}
        }
        GboardFloatingGlassOutput output = state.output;
        state.output = null;
        state.sink = null;
        state.sinkHost = null;
        if (output != null) {
            try { output.release("coordinator-release"); } catch (Throwable ignored) {}
        }
    }

    private static void restoreStockBackground(State state) {
        if (state == null) return;
        View backgroundFrame = state.backgroundFrame;
        if (backgroundFrame != null) {
            try { backgroundFrame.setAlpha(state.stockBackgroundAlpha); }
            catch (Throwable ignored) {}
        }
        state.stockHidden = false;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
