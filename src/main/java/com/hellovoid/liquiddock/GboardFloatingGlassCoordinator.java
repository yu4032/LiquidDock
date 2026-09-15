package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import java.util.WeakHashMap;

/** Owns one structurally identified Gboard floating-keyboard glass session. */
final class GboardFloatingGlassCoordinator {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final int MAX_GEOMETRY_FRAME_RETRIES = 24;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View popup;
        final GboardFloatingStructureResolver.Structure structure;
        final LiquidDockConfig.Glass glassConfig;
        final ViewGroup keyboardArea;
        ViewGroup sinkHost;
        View backgroundFrame;
        View root;
        float stockBackgroundAlpha = 1f;
        float cornerRadiusPx;
        GboardFloatingGlassSession session;
        GboardFloatingGlassView sink;
        View.OnAttachStateChangeListener attachListener;
        View.OnLayoutChangeListener layoutListener;
        boolean stockHidden;
        boolean captureRequested;
        boolean geometryRetryPosted;
        int geometryRetryCount;
        boolean released;

        State(
                View popup,
                GboardFloatingStructureResolver.Structure structure,
                LiquidDockConfig.Glass glassConfig) {
            this.popup = popup;
            this.structure = structure;
            this.glassConfig = glassConfig;
            this.keyboardArea = structure.keyboardArea;
            this.backgroundFrame = structure.stockBackground;
        }
    }

    private GboardFloatingGlassCoordinator() {}

    static synchronized void onShown(
            View popup,
            GboardFloatingStructureResolver.Structure structure,
            LiquidDockConfig.Glass glassConfig) {
        if (popup == null || structure == null || glassConfig == null) return;
        State existing = STATES.get(popup);
        if (existing != null && !existing.released) {
            if (existing.session == null && popup.isAttachedToWindow()) attachNow(existing);
            else syncGeometry(existing);
            return;
        }
        State state = new State(popup, structure, glassConfig);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                attachNow(state);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                release(state, "popup-detached");
            }
        };
        STATES.put(popup, state);
        popup.addOnAttachStateChangeListener(state.attachListener);
        if (popup.isAttachedToWindow()) attachNow(state);
        else log("popup show observed before attach", null);
    }

    static synchronized void onHidden(View popup) {
        if (popup == null) return;
        State state = STATES.remove(popup);
        if (state != null) release(state, "popup-hidden");
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
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        state.keyboardArea.addOnLayoutChangeListener(state.layoutListener);

        GboardFloatingGlassSession session = new GboardFloatingGlassSession(
                root,
                state.glassConfig,
                new GboardFloatingGlassSession.Listener() {
                    @Override public void onPresented() {
                        state.popup.post(() -> GboardFloatingGlassCoordinator.onPresented(state));
                    }

                    @Override public void onFailure(Throwable error) {
                        state.popup.post(() -> failClosed(state, "session failure", error));
                    }
                });
        state.session = session;
        GboardFloatingGlassView sink = new GboardFloatingGlassView(
                state.keyboardArea.getContext(), session);
        state.sink = sink;
        if (!insertSinkBelowKeyboardContent(state, sink)) {
            failClosed(state, "unable to insert glass below keyboard content", null);
            return;
        }
        syncGeometry(state);
        log("floating popup bound structurally root=" + root.getClass().getSimpleName()
                + " holders=" + state.structure.keyboardViewHolders.size(), null);
    }

    private static boolean insertSinkBelowKeyboardContent(State state, GboardFloatingGlassView sink) {
        if (state == null || sink == null) return false;
        ViewGroup host = state.keyboardArea;
        int contentIndex = host.indexOfChild(state.structure.contentColumn);
        if (contentIndex < 0) return false;
        try {
            // Gboard custom measurement can propagate 0x00ffffff for MATCH_PARENT. Start from a
            // concrete 1x1 surface and promote to the authoritative laid-out keyboard bounds.
            host.addView(sink, contentIndex, new ViewGroup.LayoutParams(1, 1));
            state.sinkHost = host;
            log("glass inserted below keyboard content contentIndex=" + contentIndex, null);
            return true;
        } catch (Throwable error) {
            log("glass insertion failed", error);
            return false;
        }
    }

    private static float resolveCornerRadiusPx(
            GboardFloatingStructureResolver.Structure structure) {
        if (structure == null) return 0f;
        float radius = outlineRadius(structure.keyboardArea);
        if (radius > 0f) return radius;
        radius = outlineRadius(structure.stockBackground);
        if (radius > 0f) return radius;
        return 0f;
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

    private static synchronized void syncGeometry(State state) {
        if (state == null || state.released || state.session == null
                || state.keyboardArea == null || state.root == null) return;
        syncSinkBounds(state);
        GboardFloatingGlassGeometry next = GboardFloatingGlassGeometry.capture(
                state.root, state.keyboardArea, state.cornerRadiusPx);
        if (next == null) {
            if (state.geometryRetryCount >= MAX_GEOMETRY_FRAME_RETRIES) {
                failClosed(state, "floating geometry never became valid", null);
                return;
            }
            if (!state.geometryRetryPosted) {
                state.geometryRetryPosted = true;
                state.geometryRetryCount++;
                state.keyboardArea.postOnAnimation(() -> {
                    synchronized (GboardFloatingGlassCoordinator.class) {
                        state.geometryRetryPosted = false;
                        if (state.released) return;
                    }
                    syncGeometry(state);
                });
            }
            return;
        }
        state.geometryRetryCount = 0;
        state.session.updateGeometry(next);
        if (!state.captureRequested) {
            state.captureRequested = true;
            state.session.requestInitialCapture();
        }
    }

    private static void syncSinkBounds(State state) {
        if (state == null || state.keyboardArea == null || state.sinkHost == null
                || state.sink == null) return;
        int width = state.keyboardArea.getWidth();
        int height = state.keyboardArea.getHeight();
        if (width <= 0 || height <= 0) return;
        ViewGroup.LayoutParams params = state.sink.getLayoutParams();
        if (params == null) return;
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            state.sink.setLayoutParams(params);
        }
        int[] areaLocation = new int[2];
        int[] hostLocation = new int[2];
        state.keyboardArea.getLocationInWindow(areaLocation);
        state.sinkHost.getLocationInWindow(hostLocation);
        float desiredX = areaLocation[0] - hostLocation[0];
        float desiredY = areaLocation[1] - hostLocation[1];
        if (state.sink.getX() != desiredX) state.sink.setX(desiredX);
        if (state.sink.getY() != desiredY) state.sink.setY(desiredY);
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
        log("first TextureView-consumed Prismal frame presented; stock visuals hidden", null);
    }

    private static synchronized void failClosed(State state, String reason, Throwable error) {
        if (state == null || state.released) return;
        log(reason, error);
        release(state, "fail-closed");
    }

    private static synchronized void release(State state, String reason) {
        if (state == null || state.released) return;
        state.released = true;
        if (STATES.get(state.popup) == state) STATES.remove(state.popup);
        GboardStockVisualAuthority.release(state.structure);
        restoreStockBackground(state);
        if (state.attachListener != null) {
            try { state.popup.removeOnAttachStateChangeListener(state.attachListener); }
            catch (Throwable ignored) {}
            state.attachListener = null;
        }
        if (state.keyboardArea != null && state.layoutListener != null) {
            try { state.keyboardArea.removeOnLayoutChangeListener(state.layoutListener); }
            catch (Throwable ignored) {}
            state.layoutListener = null;
        }
        GboardFloatingGlassView sink = state.sink;
        ViewGroup sinkHost = state.sinkHost;
        state.sink = null;
        state.sinkHost = null;
        if (sink != null) {
            try { sink.dispose(); } catch (Throwable ignored) {}
            if (sinkHost != null) {
                try {
                    if (sink.getParent() == sinkHost) sinkHost.removeView(sink);
                } catch (Throwable ignored) {}
            }
        }
        GboardFloatingGlassSession session = state.session;
        state.session = null;
        if (session != null) {
            try { session.shutdown(); } catch (Throwable ignored) {}
        }
        log("released reason=" + reason, null);
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
