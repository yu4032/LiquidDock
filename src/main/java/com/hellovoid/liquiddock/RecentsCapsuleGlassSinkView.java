package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** Transparent Prismal output embedded directly inside one Recents capsule. */
final class RecentsCapsuleGlassSinkView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final RecentsCapsuleGlassSession session;
    private final RecentsCapsuleGlassSession.Target target;
    private Surface outputSurface;
    private boolean disposed;

    RecentsCapsuleGlassSinkView(Context context,
                               RecentsCapsuleGlassSession session,
                               RecentsCapsuleGlassSession.Target target) {
        super(context);
        this.session = session;
        this.target = target;
        setOpaque(false);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    void reveal() {
        if (!disposed) setAlpha(1f);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(target, current);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(target, old);
        session.attachOutput(target, next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeOutput(target, Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(target, current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
