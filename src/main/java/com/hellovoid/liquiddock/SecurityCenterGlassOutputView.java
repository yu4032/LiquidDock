package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/** Transparent output sibling placed immediately behind Security Center's TurboLayout. */
final class SecurityCenterGlassOutputView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final SecurityCenterGlassSession session;
    private Surface outputSurface;
    private boolean disposed;

    private SecurityCenterGlassOutputView(Context context, SecurityCenterGlassSession session) {
        super(context);
        this.session = session;
        setOpaque(false);
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    static SecurityCenterGlassOutputView attachBefore(
            View turboLayout, SecurityCenterGlassSession session) {
        if (turboLayout == null || session == null
                || !(turboLayout.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) turboLayout.getParent();
        int turboIndex = parent.indexOfChild(turboLayout);
        if (turboIndex < 0) return null;
        SecurityCenterGlassOutputView output =
                new SecurityCenterGlassOutputView(turboLayout.getContext(), session);
        parent.addView(output, turboIndex, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return output;
    }

    void setAuthorizedVisible(boolean visible) {
        if (disposed) return;
        setAlpha(visible ? 1f : 0f);
    }

    boolean isDisposed() {
        return disposed;
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        setAlpha(0f);
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(old);
        session.attachOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // Presentation is authorized by the coordinator after the matching render callback.
    }
}
