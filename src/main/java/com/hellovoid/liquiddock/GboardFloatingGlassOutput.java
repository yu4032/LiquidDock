package com.hellovoid.liquiddock;

import android.view.Surface;

/** Presentation boundary shared by preferred SurfaceControl and TextureView fallback outputs. */
interface GboardFloatingGlassOutput {
    interface Listener {
        void onSurfaceReady(Surface surface, int width, int height);
        void onSurfaceSizeChanged(int width, int height);
        void onPresented();
        void onFailed(String reason, Throwable error);
    }

    boolean isPreferred();
    void updateGeometry(GboardFloatingGlassGeometry geometry);
    void showAfterFirstSwap();
    void release(String reason);
}
