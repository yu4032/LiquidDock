package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** TextureView output inserted as the first child of Gboard's floating keyboard area. */
final class GboardFloatingGlassView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private static final String TAG = "[DC][GboardFloatingGlass]";

    private final GboardFloatingGlassSession session;
    private Surface outputSurface;
    private boolean disposed;

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
        log("sink dispose attached=" + isAttachedToWindow()
                + " hw=" + isHardwareAccelerated()
                + " winVis=" + getWindowVisibility()
                + " view=" + getWidth() + "x" + getHeight());
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
        log("sink surface available size=" + width + "x" + height
                + " view=" + getWidth() + "x" + getHeight()
                + " attached=" + isAttachedToWindow()
                + " hw=" + isHardwareAccelerated()
                + " shown=" + isShown()
                + " winVis=" + getWindowVisibility()
                + " textureReleased=" + surfaceTexture.isReleased()
                + " surfaceValid=" + surface.isValid());
        session.attachOutput(surface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        log("sink surface size size=" + width + "x" + height
                + " view=" + getWidth() + "x" + getHeight()
                + " attached=" + isAttachedToWindow()
                + " hw=" + isHardwareAccelerated()
                + " textureReleased=" + surfaceTexture.isReleased());
        if (!disposed) session.resizeOutput(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Surface current = outputSurface;
        outputSurface = null;
        log("sink surface destroyed attached=" + isAttachedToWindow()
                + " hw=" + isHardwareAccelerated()
                + " winVis=" + getWindowVisibility()
                + " textureReleased=" + surfaceTexture.isReleased()
                + " surfaceValid=" + (current != null && current.isValid()));
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        log("sink surface updated textureReleased=" + surfaceTexture.isReleased()
                + " surfaceValid=" + (outputSurface != null && outputSurface.isValid()));
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
