package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Hosts the native-frame output and the existing TextureView fallback below Gboard content. */
final class GboardFloatingGlassView extends FrameLayout
        implements TextureView.SurfaceTextureListener {
    private final GboardFloatingGlassSession session;
    private final TextureView textureFallback;
    private final GboardFloatingGlassHwuiView hwuiOutput;
    private final boolean frozenHwuiPilot;
    private Surface outputSurface;
    private boolean disposed;

    GboardFloatingGlassView(Context context, GboardFloatingGlassSession session) {
        super(context);
        if (session == null) throw new IllegalArgumentException("session == null");
        this.session = session;
        frozenHwuiPilot = session.usesFrozenHwuiPilot();
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        textureFallback = new TextureView(context);
        textureFallback.setOpaque(false);
        textureFallback.setClickable(false);
        textureFallback.setFocusable(false);
        textureFallback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textureFallback.setSurfaceTextureListener(this);
        addView(textureFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hwuiOutput = new GboardFloatingGlassHwuiView(context,
                new GboardFloatingGlassHwuiView.Listener() {
                    @Override public void onPresented() {
                        GboardFloatingGlassView.this.session.onHwuiOutputPresented();
                    }

                    @Override public void onFailure(Throwable error) {
                        GboardFloatingGlassView.this.session.onHwuiOutputFailure(error);
                    }
                });
        addView(hwuiOutput, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (frozenHwuiPilot) {
            textureFallback.setVisibility(View.INVISIBLE);
            hwuiOutput.setVisibility(View.INVISIBLE);
            session.attachHwuiOutput(this, hwuiOutput);
        } else {
            textureFallback.setVisibility(View.VISIBLE);
            hwuiOutput.setVisibility(View.GONE);
        }
    }

    boolean usesFrozenHwuiPilot() {
        return frozenHwuiPilot;
    }

    void showHwuiOutput() {
        if (disposed || !frozenHwuiPilot) return;
        hwuiOutput.setVisibility(View.VISIBLE);
        textureFallback.setVisibility(View.INVISIBLE);
    }

    void showTextureFallback() {
        if (disposed) return;
        hwuiOutput.setVisibility(View.INVISIBLE);
        textureFallback.setVisibility(View.VISIBLE);
    }

    void showPreparingOutput() {
        if (disposed || !frozenHwuiPilot) return;
        hwuiOutput.setVisibility(View.INVISIBLE);
        textureFallback.setVisibility(View.INVISIBLE);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        hwuiOutput.clearBackdrops();
        session.detachHwuiOutput(this);
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
