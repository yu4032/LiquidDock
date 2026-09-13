package com.hellovoid.liquiddock;

import android.view.SurfaceControl;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * Runtime-only bridge from the mapping hot path to SurfaceFlinger's Dock PassBlur producer.
 *
 * <p>updateBackdropMapping() computes two rectangles back-to-back: the larger overscan sample
 * followed by the visible Dock rectangle. We recognize that pair and use only the second rectangle
 * as the canonical motion geometry. A changed canonical rectangle forces one update-texture
 * transaction even when the binding already believes updates are enabled. This is required on
 * HyperOS builds where an enabled Dock producer otherwise emits only sparse frames while the Dock
 * itself continues to move.</p>
 */
final class DockBackdropProducerMotionRuntime {
    private static final DockBackdropProducerKickState KICK_STATE =
            new DockBackdropProducerKickState();

    private static boolean havePreviousObservation;
    private static float previousHostWidth;
    private static float previousHostHeight;
    private static float previousFrameLeft;
    private static float previousFrameTop;
    private static float previousFrameWidth;
    private static float previousFrameHeight;

    private static boolean haveCanonicalDock;
    private static float lastDockLeft;
    private static float lastDockTop;
    private static float lastDockWidth;
    private static float lastDockHeight;
    private static int lastBackdropIdentity;

    private DockBackdropProducerMotionRuntime() {}

    static synchronized void observeMapping(
            float hostLeft, float hostTop, float hostWidth, float hostHeight,
            float frameLeft, float frameTop, float frameWidth, float frameHeight) {
        if (!Miuix307ZeroCopyRenderer.isInstalled()) {
            resetLocked();
            return;
        }

        // updateBackdropMapping() calls compute(sampleRect) and then compute(dockRect). The Dock
        // rectangle is the second, no-larger rectangle against exactly the same producer frame.
        boolean canonicalDockObservation = havePreviousObservation
                && sameFrame(frameLeft, frameTop, frameWidth, frameHeight)
                && hostWidth <= previousHostWidth
                && hostHeight <= previousHostHeight;

        previousHostWidth = hostWidth;
        previousHostHeight = hostHeight;
        previousFrameLeft = frameLeft;
        previousFrameTop = frameTop;
        previousFrameWidth = frameWidth;
        previousFrameHeight = frameHeight;
        havePreviousObservation = true;

        if (!canonicalDockObservation) return;

        View backdrop = currentBackdrop();
        if (backdrop == null || !backdrop.isAttachedToWindow()) return;
        int identity = System.identityHashCode(backdrop);
        if (identity != lastBackdropIdentity) {
            lastBackdropIdentity = identity;
            haveCanonicalDock = false;
            KICK_STATE.reset();
        }

        boolean changed = !haveCanonicalDock
                || Float.compare(lastDockLeft, hostLeft) != 0
                || Float.compare(lastDockTop, hostTop) != 0
                || Float.compare(lastDockWidth, hostWidth) != 0
                || Float.compare(lastDockHeight, hostHeight) != 0;
        lastDockLeft = hostLeft;
        lastDockTop = hostTop;
        lastDockWidth = hostWidth;
        lastDockHeight = hostHeight;
        haveCanonicalDock = true;
        if (!changed) return;

        DockBackdropProducerKickState.Decision decision = KICK_STATE.onGeometryChanged();
        if (!decision.postKick) return;
        try {
            forceCurrentDockProducer(backdrop);
        } finally {
            KICK_STATE.onKickConsumed();
        }
    }

    private static boolean sameFrame(
            float left, float top, float width, float height) {
        return Float.compare(previousFrameLeft, left) == 0
                && Float.compare(previousFrameTop, top) == 0
                && Float.compare(previousFrameWidth, width) == 0
                && Float.compare(previousFrameHeight, height) == 0;
    }

    @SuppressWarnings("unchecked")
    private static View currentBackdrop() {
        try {
            Field field = Miuix307ZeroCopyRenderer.class.getDeclaredField("gpuBackdropRef");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof WeakReference)) return null;
            Object view = ((WeakReference<?>) value).get();
            return view instanceof View ? (View) view : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void forceCurrentDockProducer(View backdrop) {
        try {
            Object enabled = HookUtil.getField(backdrop, "producerUpdatesEnabled");
            if (enabled instanceof Boolean && !((Boolean) enabled)) return;
            Object value = HookUtil.getField(backdrop, "binding");
            if (!(value instanceof Miuix307PassBlurBridge.Binding)) return;
            Miuix307PassBlurBridge.Binding binding = (Miuix307PassBlurBridge.Binding) value;
            if (!binding.bound || binding.domain != PassBlurDomain.DOCK
                    || binding.rootSurface == null || !binding.rootSurface.isValid()) return;

            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                binding.setUpdateTextureFlag.invoke(
                        transaction,
                        binding.rootSurface,
                        Boolean.TRUE,
                        Float.valueOf(binding.scale));
                transaction.apply();
            }
            View root = backdrop.getRootView();
            if (root != null) root.postInvalidateOnAnimation();
        } catch (Throwable error) {
            MainHook.log("[DC][PBTX][MotionKick] force update failed: " + error);
        }
    }

    static synchronized void reset() {
        resetLocked();
    }

    private static void resetLocked() {
        havePreviousObservation = false;
        haveCanonicalDock = false;
        lastBackdropIdentity = 0;
        KICK_STATE.reset();
    }
}
