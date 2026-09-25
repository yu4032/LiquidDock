package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/**
 * Local ShortcutMenu material output.
 *
 * <p>The layer lives inside MIUIX mContentView at index 0, so vendor bounds/alpha/radius animation
 * remains the sole presentation authority. The Surface contains only the root-space crop covered by
 * the menu rather than a full-screen scene scaled into local bounds.</p>
 */
final class ShortcutPopupGlassLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private final ShortcutPopupGlassSession session;
    private Surface outputSurface;
    private boolean disposed;

    ShortcutPopupGlassLayer(Context context, ShortcutPopupGlassSession session) {
        super(context);
        this.session = session;
        setOpaque(false);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    void reveal() {
        if (!disposed) {
            animate().cancel();
            setAlpha(1f);
        }
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        animate().cancel();
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(old);
        session.attachOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
