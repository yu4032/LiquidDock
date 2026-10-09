package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import java.util.WeakHashMap;

/** Owns one structurally identified Gboard floating-keyboard glass session. */
final class GboardFloatingGlassCoordinator {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final int MAX_GEOMETRY_FRAME_RETRIES = 24;
    private static final long FROZEN_STARTUP_MIN_LIVE_MS = 360L;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        final View popup;
        final GboardFloatingStructureResolver.Structure structure;
        LiquidDockConfig.Glass glassConfig;
        final ViewGroup keyboardArea;
        final boolean realtimeBackgroundSampling;
        final GboardFrozenBackdropMotionState frozenMotionState =
                new GboardFrozenBackdropMotionState();
        ViewGroup sinkHost;
        View root;
        float cornerRadiusPx;
        GboardFloatingGlassSession session;
        GboardFloatingGlassView sink;
        View.OnAttachStateChangeListener attachListener;
        View.OnLayoutChangeListener layoutListener;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        boolean stockHidden;
        boolean captureRequested;
        boolean outputPresented;
        long presentedAtMs;
        boolean attachPosted;
        GboardFloatingGlassGeometry lastGeometry;
        boolean geometryRetryPosted;
        int geometryRetryCount;
        boolean released;

        State(
                View popup,
                GboardFloatingStructureResolver.Structure structure,
                LiquidDockConfig.Glass glassConfig,
                boolean realtimeBackgroundSampling) {
            this.popup = popup;
            this.structure = structure;
            this.glassConfig = glassConfig;
            this.keyboardArea = structure.keyboardArea;
            this.realtimeBackgroundSampling = realtimeBackgroundSampling;
        }
    }

    private GboardFloatingGlassCoordinator() {}

    static synchronized void onLiveConfigChanged(ConfigReader reader, LiquidDockConfig config) {
        if (reader == null || config == null) return;
        ThirdPartyGlassAppearance appearance = GboardGlassPreferences.resolveShared(reader, config.glass);
        boolean enabled = config.enabled && config.glass.enabled && appearance.enabled;
        boolean realtime = GboardGlassPreferences.realtimeBackgroundSampling(reader);
        for (State state : new java.util.ArrayList<>(STATES.values())) {
            if (state == null || state.released) continue;
            if (!enabled) {
                release(state);
            } else if (state.realtimeBackgroundSampling != realtime) {
                // The frozen/live producer policy is a session-lifetime choice.
                // Transfer ownership through the existing safe shutdown/acquire path.
                release(state);
                onShown(state.popup, state.structure, config.glass, realtime);
            } else {
                state.glassConfig = config.glass;
                if (state.session != null) state.session.applyLiveGlassConfig(config.glass, appearance);
            }
        }
        GboardFloatingGlassHook.onLiveConfigChanged(enabled);
    }

    static synchronized void onShown(
            View popup,
            GboardFloatingStructureResolver.Structure structure,
            LiquidDockConfig.Glass glassConfig,
            boolean realtimeBackgroundSampling) {
        if (popup == null || structure == null || glassConfig == null) return;
        State existing = STATES.get(popup);
        if (existing != null && !existing.released) {
            if (existing.realtimeBackgroundSampling != realtimeBackgroundSampling) {
                release(existing);
            } else {
                if (existing.session == null && popup.isAttachedToWindow()) scheduleAttach(existing);
                else syncGeometry(existing);
                return;
            }
        }
        State state = new State(
                popup, structure, glassConfig, realtimeBackgroundSampling);
        state.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                scheduleAttach(state);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                release(state);
            }
        };
        STATES.put(popup, state);
        popup.addOnAttachStateChangeListener(state.attachListener);
        if (popup.isAttachedToWindow()) scheduleAttach(state);
    }

    private static synchronized void scheduleAttach(State state) {
        if (state == null || state.released || state.attachPosted || state.session != null
                || !state.popup.isAttachedToWindow()) return;
        state.attachPosted = true;
        state.popup.post(() -> {
            synchronized (GboardFloatingGlassCoordinator.class) {
                state.attachPosted = false;
                if (state.released || state.session != null
                        || !state.popup.isAttachedToWindow()) return;
            }
            attachNow(state);
        });
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
        state.layoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> syncGeometry(state);
        state.keyboardArea.addOnLayoutChangeListener(state.layoutListener);
        state.preDrawListener = () -> {
            syncGeometry(state);
            return true;
        };
        ViewTreeObserver observer = state.keyboardArea.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(state.preDrawListener);

        GboardFloatingGlassSession session = new GboardFloatingGlassSession(
                root,
                state.glassConfig,
                state.realtimeBackgroundSampling,
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
    }

    private static boolean insertSinkBelowKeyboardContent(
            State state, GboardFloatingGlassView sink) {
        if (state == null || sink == null || !(state.root instanceof ViewGroup)) return false;
        ViewGroup host = (ViewGroup) state.root;
        View anchor = directChildUnder(state.keyboardArea, host);
        if (anchor == null && state.keyboardArea != host) return false;
        int index = anchor != null ? host.indexOfChild(anchor) : 0;
        if (index < 0) return false;
        try {
            host.addView(
                    sink,
                    index,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            state.sinkHost = host;
            return true;
        } catch (Throwable error) {
            log("fullscreen glass insertion failed", error);
            return false;
        }
    }

    private static View directChildUnder(View descendant, ViewGroup ancestor) {
        if (descendant == null || ancestor == null || descendant == ancestor) return null;
        View current = descendant;
        android.view.ViewParent parent = current.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) return current;
            current = (View) parent;
            parent = current.getParent();
        }
        return null;
    }

    private static float resolveCornerRadiusPx(
            GboardFloatingStructureResolver.Structure structure) {
        if (structure == null) return 0f;
        float radius = outlineRadius(structure.stockBackground);
        if (radius > 0f) return radius;
        radius = outlineRadius(structure.keyboardArea);
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
                || state.sinkHost == null || state.root == null) return;
        GboardFloatingGlassGeometry next = GboardFloatingGlassGeometry.capture(
                state.root, state.sinkHost, state.structure, state.cornerRadiusPx);
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
        GboardFloatingGlassGeometry previous = state.lastGeometry;
        boolean geometryChanged = geometryChanged(previous, next);
        if (!state.realtimeBackgroundSampling && state.captureRequested) {
            boolean startupFreezeAllowed = state.outputPresented
                    && state.presentedAtMs > 0L
                    && SystemClock.uptimeMillis() - state.presentedAtMs
                    >= FROZEN_STARTUP_MIN_LIVE_MS;
            GboardFrozenBackdropMotionState.Decision decision =
                    state.frozenMotionState.onFrame(startupFreezeAllowed, geometryChanged);
            if (decision == GboardFrozenBackdropMotionState.Decision.FREEZE_AFTER_SETTLE) {
                state.session.freezeBackdropAfterSettle();
            } else if (decision
                    == GboardFrozenBackdropMotionState.Decision.REFRESH_AT_MOTION_START) {
                state.session.requestFrozenMotionCapture();
            }
        }
        state.lastGeometry = next;
        state.session.updateGeometry(next);
        if (!state.captureRequested) {
            state.captureRequested = true;
            state.session.requestInitialCapture();
        }
    }

    private static boolean geometryChanged(
            GboardFloatingGlassGeometry first,
            GboardFloatingGlassGeometry second) {
        if (first == null || second == null) return false;
        return first.rootWidth != second.rootWidth
                || first.rootHeight != second.rootHeight
                || Math.abs(first.left - second.left) >= 0.25f
                || Math.abs(first.top - second.top) >= 0.25f
                || Math.abs(first.width - second.width) >= 0.25f
                || Math.abs(first.height - second.height) >= 0.25f;
    }

    private static synchronized void onPresented(State state) {
        if (state == null || state.released || state.stockHidden) return;
        View backgroundFrame = state.structure.stockBackground;
        if (backgroundFrame == null || !backgroundFrame.isAttachedToWindow()) return;
        if (!GboardStockVisualAuthority.claim(state.structure)) {
            failClosed(state, "unable to claim floating stock visuals", null);
            return;
        }
        state.stockHidden = true;
        state.outputPresented = true;
        state.presentedAtMs = SystemClock.uptimeMillis();
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
        if (state.stockHidden) {
            state.popup.post(() -> GboardStockVisualAuthority.release(state.structure));
        }
        state.stockHidden = false;
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
        if (state.keyboardArea != null && state.preDrawListener != null) {
            try {
                ViewTreeObserver observer = state.keyboardArea.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(state.preDrawListener);
            } catch (Throwable ignored) {}
            state.preDrawListener = null;
        }
        GboardFloatingGlassView sink = state.sink;
        ViewGroup sinkHost = state.sinkHost;
        state.sink = null;
        state.sinkHost = null;
        if (sink != null) {
            if (sinkHost != null) {
                sinkHost.post(() -> {
                    try { sink.dispose(); } catch (Throwable ignored) {}
                    try {
                        if (sink.getParent() == sinkHost) sinkHost.removeView(sink);
                    } catch (Throwable ignored) {}
                });
            } else {
                try { sink.dispose(); } catch (Throwable ignored) {}
            }
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
