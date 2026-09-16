package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** TextureView output placed below Gboard floating-keyboard content. */
final class GboardFloatingGlassView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final GboardFloatingGlassOutput.Listener listener;
    private boolean disposed;
    private long presentedSerial;

    GboardFloatingGlassView(Context context, GboardFloatingGlassOutput.Listener listener) {
        super(context);
        if (listener == null) throw new IllegalArgumentException("listener == null");
        this.listener = listener;
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    /** Compatibility bridge for the handwriting capsule, which is not part of this output migration. */
    GboardFloatingGlassView(Context context, GboardFloatingGlassSession session) {
        this(context, new GboardFloatingGlassOutput.Listener() {
            @Override public void onSurfaceReady(Surface surface, int width, int height) {
                session.attachOutput(surface, width, height);
            }
            @Override public void onSurfaceSizeChanged(int width, int height) {
                session.resizeOutput(width, height);
            }
            @Override public void onPresented() {
                session.onOutputPresented();
            }
            @Override public void onFailed(String reason, Throwable error) {
                try {
                    Api101Bridge.log("[DC][GboardFloatingGlass] handwriting output failure "
                            + reason, error);
                } catch (Throwable ignored) {}
            }
        });
        if (session == null) throw new IllegalArgumentException("session == null");
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (disposed) return;
        GboardDragDiagnostics.log("TEXTURE_AVAILABLE size=" + width + "x" + height);
        try {
            Surface surface = new Surface(surfaceTexture);
            requestDisplayFrameRate(surface);
            listener.onSurfaceReady(surface, width, height);
        } catch (Throwable error) {
            listener.onFailed("texture-surface-available", error);
        }
    }

    private void requestDisplayFrameRate(Surface surface) {
        if (surface == null) return;
        Display display = getDisplay();
        float displayHz = display != null ? display.getRefreshRate() : Float.NaN;
        float preferredHz = GboardFloatingFrameRatePolicy.preferredHz(displayHz);
        try {
            surface.setFrameRate(preferredHz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            GboardDragDiagnostics.log("SURFACE_FRAME_RATE requested=" + preferredHz
                    + " display=" + displayHz);
        } catch (Throwable error) {
            GboardDragDiagnostics.log("SURFACE_FRAME_RATE_FAIL requested=" + preferredHz
                    + " cause=" + error.getClass().getName());
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        if (!disposed) {
            GboardDragDiagnostics.log("TEXTURE_RESIZED size=" + width + "x" + height);
            listener.onSurfaceSizeChanged(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        GboardDragDiagnostics.log("TEXTURE_DESTROYED");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        if (!disposed) {
            presentedSerial++;
            GboardDragDiagnostics.log("TEXTURE_PRESENTED serial=" + presentedSerial);
            listener.onPresented();
        }
    }
}
