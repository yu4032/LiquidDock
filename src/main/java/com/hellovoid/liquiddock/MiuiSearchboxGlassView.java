package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** Stable full-screen Prismal output; only glass geometry moves over the frozen backdrop. */
final class MiuiSearchboxGlassView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final MiuiSearchboxGlassSession session;
    private Surface outputSurface;
    private boolean disposed;

    MiuiSearchboxGlassView(Context context, MiuiSearchboxGlassSession session) {
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
        Surface surface = new Surface(surfaceTexture);
        outputSurface = surface;
        Api101Bridge.log("[DC][LockScreenClockGlass] output surface available "
                + width + "x" + height);
        session.attachOutput(surface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        if (!disposed) session.resizeOutput(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        if (!disposed) session.onOutputPresented();
    }
}
