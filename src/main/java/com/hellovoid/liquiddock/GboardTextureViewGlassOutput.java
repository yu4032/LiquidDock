package com.hellovoid.liquiddock;

import android.content.Context;

/** Compatibility presentation path backed by the existing TextureView consumer. */
final class GboardTextureViewGlassOutput implements GboardFloatingGlassOutput {
    private final GboardFloatingGlassView view;
    private boolean released;

    static GboardTextureViewGlassOutput create(Context context, Listener listener) {
        if (context == null || listener == null) return null;
        return new GboardTextureViewGlassOutput(context, listener);
    }

    private GboardTextureViewGlassOutput(Context context, Listener listener) {
        view = new GboardFloatingGlassView(context, listener);
    }

    GboardFloatingGlassView view() {
        return view;
    }

    @Override public boolean isPreferred() {
        return false;
    }

    @Override public void updateGeometry(GboardFloatingGlassGeometry geometry) {
        // TextureView remains positioned by its parent View hierarchy.
    }

    @Override public void showAfterFirstSwap() {
        // TextureView is already visible; presentation callback comes from SurfaceTexture update.
    }

    @Override public void release(String reason) {
        if (released) return;
        released = true;
        view.dispose();
    }
}
