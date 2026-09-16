package com.hellovoid.liquiddock;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.AttachedSurfaceControl;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

/** Preferred compositor-owned presentation path for Gboard floating glass. */
final class GboardSurfaceControlGlassOutput implements GboardFloatingGlassOutput {
    private static final String TAG = "[DC][GboardSurfaceControlGlass]";
    private static final int LAYER_BELOW_ROOT_CONTENT = -1;

    private final View root;
    private final Listener listener;
    private final AttachedSurfaceControl attachedRoot;
    private final SurfaceControl surfaceControl;
    private final Surface surface;

    private boolean released;
    private boolean shown;
    private boolean presentationReported;
    private int width = 1;
    private int height = 1;

    static GboardSurfaceControlGlassOutput create(View root, Listener listener) {
        if (root == null || listener == null || Build.VERSION.SDK_INT < 31
                || !root.isAttachedToWindow()) return null;
        try {
            return new GboardSurfaceControlGlassOutput(root, listener);
        } catch (Throwable error) {
            try {
                Api101Bridge.log(TAG + " create failed", error);
            } catch (Throwable ignored) {}
            return null;
        }
    }

    private GboardSurfaceControlGlassOutput(View root, Listener listener) {
        this.root = root;
        this.listener = listener;
        AttachedSurfaceControl attached = root.getRootSurfaceControl();
        if (attached == null) throw new IllegalStateException("root surface control unavailable");
        attachedRoot = attached;

        SurfaceControl child = null;
        Surface childSurface = null;
        SurfaceControl.Transaction transaction = null;
        try {
            child = new SurfaceControl.Builder()
                    .setName("LiquidDock Gboard Floating Glass")
                    .setBufferSize(1, 1)
                    .setFormat(PixelFormat.TRANSLUCENT)
                    .setOpaque(false)
                    .setHidden(true)
                    .build();
            if (child == null || !child.isValid()) {
                throw new IllegalStateException("surface control invalid");
            }
            transaction = attachedRoot.buildReparentTransaction(child);
            if (transaction == null) {
                throw new IllegalStateException("root reparent transaction unavailable");
            }
            transaction.setLayer(child, LAYER_BELOW_ROOT_CONTENT);
            transaction.setPosition(child, 0f, 0f);
            transaction.setCrop(child, new Rect(0, 0, 1, 1));
            transaction.apply();
            childSurface = new Surface(child);
            if (!childSurface.isValid()) {
                throw new IllegalStateException("surface invalid");
            }
        } catch (Throwable error) {
            if (childSurface != null) {
                try { childSurface.release(); } catch (Throwable ignored) {}
            }
            if (child != null) {
                try { child.release(); } catch (Throwable ignored) {}
            }
            throw error;
        } finally {
            if (transaction != null) {
                try { transaction.close(); } catch (Throwable ignored) {}
            }
        }
        surfaceControl = child;
        surface = childSurface;
        listener.onSurfaceReady(surface, width, height);
    }

    @Override public boolean isPreferred() {
        return true;
    }

    @Override public void updateGeometry(GboardFloatingGlassGeometry geometry) {
        if (released || geometry == null || !surfaceControl.isValid()) return;
        int targetWidth = Math.max(1, (int) Math.ceil(geometry.width));
        int targetHeight = Math.max(1, (int) Math.ceil(geometry.height));
        GboardSurfaceControlGeometryPolicy.Frame frame =
                GboardSurfaceControlGeometryPolicy.from(
                        geometry.left, geometry.top, targetWidth, targetHeight);
        boolean sizeChanged = frame.width != width || frame.height != height;
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        try {
            if (sizeChanged) {
                transaction.setBufferSize(surfaceControl, frame.width, frame.height);
            }
            transaction.setPosition(surfaceControl, frame.x, frame.y);
            transaction.setCrop(surfaceControl, new Rect(0, 0, frame.width, frame.height));
            transaction.apply();
            if (sizeChanged) {
                width = frame.width;
                height = frame.height;
                listener.onSurfaceSizeChanged(width, height);
            }
        } catch (Throwable error) {
            fail("geometry-update", error);
        } finally {
            try { transaction.close(); } catch (Throwable ignored) {}
        }
    }

    @Override public void showAfterFirstSwap() {
        if (released || shown || !surfaceControl.isValid()) return;
        shown = true;
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        try {
            transaction.setVisibility(surfaceControl, true);
            transaction.addTransactionCommittedListener(
                    command -> root.post(command),
                    () -> {
                        if (released || presentationReported) return;
                        presentationReported = true;
                        listener.onPresented();
                    });
            transaction.apply();
        } catch (Throwable error) {
            shown = false;
            fail("show-after-first-swap", error);
        } finally {
            try { transaction.close(); } catch (Throwable ignored) {}
        }
    }

    @Override public void release(String reason) {
        if (released) return;
        released = true;
        SurfaceControl.Transaction transaction = null;
        try {
            if (surfaceControl.isValid()) {
                transaction = new SurfaceControl.Transaction();
                transaction.reparent(surfaceControl, null);
                transaction.apply();
            }
        } catch (Throwable error) {
            try { Api101Bridge.log(TAG + " release transaction failed reason=" + reason, error); }
            catch (Throwable ignored) {}
        } finally {
            if (transaction != null) {
                try { transaction.close(); } catch (Throwable ignored) {}
            }
            try { surface.release(); } catch (Throwable ignored) {}
            try { surfaceControl.release(); } catch (Throwable ignored) {}
        }
    }

    private void fail(String reason, Throwable error) {
        if (released) return;
        try { listener.onFailed(reason, error); }
        catch (Throwable ignored) {}
    }
}
