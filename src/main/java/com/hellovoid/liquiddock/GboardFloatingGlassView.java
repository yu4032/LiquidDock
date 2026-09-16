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
    private final GboardFloatingGlassSession session;
    private Surface outputSurface;
    private boolean disposed;
    private long presentedSerial;

    GboardFloatingGlassView(Context context, GboardFloatingGlassSession session) {
        super(context);
        if (session == null) throw new IllegalArgumentException("session == null");
        this.session = session;
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (disposed) return;
        GboardDragDiagnostics.log("TEXTURE_AVAILABLE size=" + width + "x" + height);
        Surface surface = new Surface(surfaceTexture);
        requestDisplayFrameRate(surface);
        outputSurface = surface;
        session.attachOutput(surface, width, height);
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
            session.resizeOutput(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        GboardDragDiagnostics.log("TEXTURE_DESTROYED");
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        if (!disposed) {
            presentedSerial++;
            GboardDragDiagnostics.log("TEXTURE_PRESENTED serial=" + presentedSerial);
            session.onOutputPresented();
        }
    }
}
