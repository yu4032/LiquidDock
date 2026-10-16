package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

/** Stable full-screen popup glass output. Its Surface never follows MiuiX popup bounds animation. */
final class ShortcutPopupGlassLayer extends TextureView implements TextureView.SurfaceTextureListener {

    private final ShortcutPopupGlassSession session;
    private Surface outputSurface;
    private boolean disposed;
    private final Rect recentsOutlineBounds = new Rect();
    private float recentsOutlineRadius;
    private boolean recentsOutlineActive;
    private ViewOutlineProvider originalOutlineProvider;
    private final ViewOutlineProvider recentsOutline = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            if (recentsOutlineActive) {
                outline.setRoundRect(recentsOutlineBounds, recentsOutlineRadius);
            } else {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        }
    };

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

    /** Crop the frozen full-screen wallpaper using the native folder's
     * animated rounded silhouette. Only HWUI clips this TextureView: there
     * is no second GL mask, producer read or synthetic time animation.
     */
    void setFolderRecentsOutline(OpenFolderGlassGeometry.Frame frame, int rootWidth, int rootHeight) {
        if (disposed || frame == null || rootWidth <= 0 || rootHeight <= 0) return;
        int left = Math.max(0, (int) Math.floor(frame.left));
        int top = Math.max(0, (int) Math.floor(frame.top));
        int right = Math.min(rootWidth, (int) Math.ceil(frame.right));
        int bottom = Math.min(rootHeight, (int) Math.ceil(frame.bottom));
        if (right <= left || bottom <= top) return;
        float radius = Math.max(0f, Math.min(frame.radius,
                Math.min(right - left, bottom - top) * .5f));
        if (recentsOutlineActive && recentsOutlineBounds.left == left
                && recentsOutlineBounds.top == top && recentsOutlineBounds.right == right
                && recentsOutlineBounds.bottom == bottom
                && Math.abs(recentsOutlineRadius - radius) < .01f) return;
        recentsOutlineBounds.set(left, top, right, bottom);
        recentsOutlineRadius = radius;
        if (!recentsOutlineActive) {
            originalOutlineProvider = getOutlineProvider();
            recentsOutlineActive = true;
            setOutlineProvider(recentsOutline);
            setClipToOutline(true);
        }
        invalidateOutline();
    }

    void clearFolderRecentsOutline() {
        if (!recentsOutlineActive) return;
        recentsOutlineActive = false;
        setClipToOutline(false);
        setOutlineProvider(originalOutlineProvider);
        originalOutlineProvider = null;
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

    /** Only dissolve this stable folder-icon buffer AFTER the resident
     * Workspace has swapped and revealed its own matching glass output.
     * Never begin this alpha transition while the Workspace is covered.
     */
    void fadeIntoWorkspace(Runnable onComplete) {
        if (disposed) return;
        animate().cancel();
        animate().alpha(0f).setDuration(80L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> { if (!disposed && onComplete != null) onComplete.run(); })
                .start();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        animate().cancel();
        clearFolderRecentsOutline();
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
