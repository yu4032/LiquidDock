package com.hellovoid.liquiddock;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.WeakHashMap;

/** Owns only the bottom visual layer of Gboard's popup floating keyboard. */
final class GboardFloatingGlassCoordinator {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final int KEYBOARD_AREA_ID = 0x7f0b0617;
    private static final int KEYBOARD_BACKGROUND_FRAME_ID = 0x7f0b0618;
    private static final int KEYBOARD_BOTTOM_FRAME_ID = 0x7f0b061b;
    private static final int FLOATING_CORNER_RADIUS_DIMEN = 0x7f0701de;
    private static final int MAX_GEOMETRY_FRAME_RETRIES = 24;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View popup;
        final LiquidDockConfig.Glass glassConfig;
        ViewGroup keyboardArea;
        ViewGroup sinkHost;
        View backgroundFrame;
        View bottomFrame;
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

        State(View popup, LiquidDockConfig.Glass glassConfig) {
            this.popup = popup;
            this.glassConfig = glassConfig;
        }
    }

    private GboardFloatingGlassCoordinator() {}

    static synchronized void onShown(View popup, LiquidDockConfig.Glass glassConfig) {
        if (popup == null || glassConfig == null) return;
        State existing = STATES.get(popup);
        if (existing != null && !existing.released) {
            if (existing.session == null && popup.isAttachedToWindow()) attachNow(existing);
            else syncGeometry(existing);
            return;
        }
        State state = new State(popup, glassConfig);
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
        View areaCandidate = state.popup.findViewById(KEYBOARD_AREA_ID);
        View backgroundFrame = state.popup.findViewById(KEYBOARD_BACKGROUND_FRAME_ID);
        View bottomFrame = state.popup.findViewById(KEYBOARD_BOTTOM_FRAME_ID);
        if (!(areaCandidate instanceof ViewGroup) || backgroundFrame == null || bottomFrame == null) {
            failClosed(state, "decompiled floating keyboard anchors unavailable", null);
            return;
        }
        ViewGroup keyboardArea = (ViewGroup) areaCandidate;
        View root = state.popup.getRootView();
        if (root == null || !root.isAttachedToWindow()) {
            failClosed(state, "popup root unavailable", null);
            return;
        }
        final float cornerRadiusPx;
        try {
            cornerRadiusPx = keyboardArea.getResources()
                    .getDimension(FLOATING_CORNER_RADIUS_DIMEN);
        } catch (Resources.NotFoundException error) {
            failClosed(state, "floating radius resource unavailable", error);
            return;
        }
        if (cornerRadiusPx <= 0f) {
            failClosed(state, "floating radius is not positive", null);
            return;
        }

        state.keyboardArea = keyboardArea;
        state.backgroundFrame = backgroundFrame;
        state.bottomFrame = bottomFrame;
        state.stockBackgroundAlpha = backgroundFrame.getAlpha();
        state.root = root;
        state.cornerRadiusPx = cornerRadiusPx;
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        keyboardArea.addOnLayoutChangeListener(state.layoutListener);

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
                keyboardArea.getContext(), session);
        state.sink = sink;
        if (!insertSinkAboveStockBackground(state, sink, backgroundFrame)) {
            failClosed(state, "unable to insert glass above stock background", null);
            return;
        }
        syncGeometry(state);
        log("floating popup bound root=" + root.getClass().getSimpleName(), null);
    }

    private static boolean insertSinkAboveStockBackground(
            State state, GboardFloatingGlassView sink, View backgroundFrame) {
        if (state == null || sink == null || backgroundFrame == null) return false;
        ViewParent parent = backgroundFrame.getParent();
        if (!(parent instanceof ViewGroup)) return false;
        ViewGroup host = (ViewGroup) parent;
        int backgroundIndex = host.indexOfChild(backgroundFrame);
        if (backgroundIndex < 0) return false;
        try {
            // Do not use MATCH_PARENT here. Gboard propagates 0x00ffffff as an internal
            // unconstrained measurement sentinel, which would make TextureView request an
            // impossible 16777215x16777215 GraphicBuffer. Start concrete, then track the
            // keyboard area's real laid-out bounds in host-local coordinates.
            host.addView(sink, backgroundIndex + 1, new ViewGroup.LayoutParams(1, 1));
            state.sinkHost = host;
            log("glass inserted above stock background host="
                    + host.getClass().getSimpleName() + " backgroundIndex=" + backgroundIndex,
                    null);
            return true;
        } catch (Throwable error) {
            log("glass insertion failed", error);
            return false;
        }
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
        if (backgroundFrame == null || !backgroundFrame.isAttachedToWindow()
                || state.keyboardArea == null || state.bottomFrame == null) return;
        if (!GboardStockVisualAuthority.claim(state.keyboardArea, state.bottomFrame)) {
            failClosed(state, "unable to claim floating stock visuals", null);
            return;
        }
        backgroundFrame.setAlpha(0f);
        state.stockHidden = true;
        log("first Prismal frame presented; all stock fills and shadow hidden", null);
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
        GboardStockVisualAuthority.release(state.keyboardArea, state.bottomFrame);
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
