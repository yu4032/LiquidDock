package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** Transparent SystemUI-root output for lockscreen clock Prismal glass. */
final class LockScreenClockGlassView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final LockScreenClockGlassSession session;
    private Surface outputSurface;
    private boolean disposed;

    LockScreenClockGlassView(Context context, LockScreenClockGlassSession session) {
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
        session.attachOutput(surface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture, int width, int height) {
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
