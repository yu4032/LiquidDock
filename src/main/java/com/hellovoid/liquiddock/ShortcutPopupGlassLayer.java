package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Stable root-sized glass output with an optional same-frame expanded-folder renderer. */
final class ShortcutPopupGlassLayer extends FrameLayout
        implements TextureView.SurfaceTextureListener {

    private final ShortcutPopupGlassSession session;
    private final TextureView textureView;
    private final ShortcutPopupGlassHwuiView hwuiView;
    private Surface outputSurface;
    private boolean disposed;
    private boolean hwuiReady;

    ShortcutPopupGlassLayer(Context context, ShortcutPopupGlassSession session) {
        super(context);
        this.session = session;
        setBackgroundColor(0x00000000);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        textureView = new TextureView(context);
        textureView.setOpaque(false);
        textureView.setClickable(false);
        textureView.setFocusable(false);
        textureView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textureView.setSurfaceTextureListener(this);
        addView(textureView, matchParent());

        // Folder zoom follows Launcher pre-draw geometry. Render its cached
        // backdrop in this ViewRoot even when the optional popup/Gboard HWUI
        // pilot is disabled, avoiding a fullscreen EGL swap on every spring frame.
        if (BuildConfig.WORKSPACE_GLASS_HWUI_PROBE || session.isFolderBackdrop()) {
            hwuiView = new ShortcutPopupGlassHwuiView(context, session);
            hwuiView.setVisibility(View.INVISIBLE);
            hwuiView.setFailureListener(this::fallbackToTextureView);
            addView(hwuiView, matchParent());
            textureView.setVisibility(View.GONE);
        } else {
            hwuiView = null;
        }
    }

    private static LayoutParams matchParent() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    boolean isAvailable() { return textureView.isAvailable(); }

    boolean setHwuiBackdrops(Bitmap raw, Bitmap blurred, int width, int height) {
        if (disposed || hwuiView == null) return false;
        if (!hwuiView.setBackdrops(raw, blurred, width, height)) {
            return false;
        }
        hwuiReady = true;
        session.setHwuiOutputActive(true);
        hwuiView.setVisibility(View.VISIBLE);
        MainHook.log("[DC][ShortcutPopupGlass] HWUI lens active raw="
                + raw.getWidth() + "x" + raw.getHeight()
                + " blur=" + blurred.getWidth() + "x" + blurred.getHeight());
        return true;
    }

    void updateFolderFrame(
            LauncherGlassGeometry.Snapshot geometry, float progress, boolean closing) {
        if (closing) {
            float sourceAlpha = LauncherGlassReturnFade.alpha(1f - progress);
            float glassAlpha = 1f - sourceAlpha;
            if (Math.abs(getAlpha() - glassAlpha) > 0.001f) setAlpha(glassAlpha);
        }
        if (hwuiView != null) hwuiView.updateFrame(geometry, progress);
    }

    void updatePopupGeometry(LauncherGlassGeometry.Snapshot geometry) {
        if (hwuiView != null) hwuiView.updateFrame(geometry, 1f);
    }

    void setMotionQualityActive(boolean active) {
        if (hwuiView != null) hwuiView.invalidate();
    }

    void invalidateHwuiFrame() {
        if (hwuiView != null) hwuiView.invalidate();
    }

    void fallbackToTextureView() {
        if (disposed) return;
        boolean wasHwuiReady = hwuiReady;
        hwuiReady = false;
        if (wasHwuiReady) {
            MainHook.log("[DC][OpenFolderGlass] HWUI lens failed; restoring TextureView output");
        }
        session.setHwuiOutputActive(false);
        if (hwuiView != null) {
            hwuiView.clearBackdrops();
            hwuiView.setVisibility(View.INVISIBLE);
        }
        textureView.setVisibility(View.VISIBLE);
    }

    void reveal() {
        if (!disposed) {
            animate().cancel();
            setAlpha(1f);
        }
    }

    void fadeOutFast() {
        if (disposed) return;
        animate().cancel();
        animate()
                .alpha(0f)
                .setDuration(AnimationRuntimeState.shortcutPopupDismissFadeDurationMs())
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    void fadeOutFolder(Runnable onEnd) {
        if (disposed) {
            if (onEnd != null) onEnd.run();
            return;
        }
        animate().cancel();
        animate().alpha(0f).setDuration(120L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(onEnd).start();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        animate().cancel();
        if (hwuiView != null) hwuiView.clearBackdrops();
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
