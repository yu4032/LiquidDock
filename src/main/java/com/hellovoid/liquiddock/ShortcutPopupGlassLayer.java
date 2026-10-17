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

/** Stable root-sized transparent popup/folder lens output; geometry is rendered by Prismal. */
final class ShortcutPopupGlassLayer extends TextureView implements TextureView.SurfaceTextureListener {

    private final ShortcutPopupGlassSession session;
    private Surface outputSurface;
    private boolean disposed;
    private final Rect backdropOutlineBounds = new Rect();
    private float backdropOutlineRadius;
    private boolean backdropOutlineActive;
    private ViewOutlineProvider originalOutlineProvider;
    private final ViewOutlineProvider backdropOutline = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            if (backdropOutlineActive) {
                outline.setRoundRect(backdropOutlineBounds, backdropOutlineRadius);
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

    /** Clip the resident transparent lens output during native folder CLOSE.
     * This View outline follows the same native progress as Prismal geometry;
     * Recents never draws a second full-screen wallpaper through this layer.
     */
    void setFolderBackdropOutline(OpenFolderGlassGeometry.Frame frame, int rootWidth, int rootHeight) {
        if (disposed || frame == null || rootWidth <= 0 || rootHeight <= 0) return;
        int left = Math.max(0, (int) Math.floor(frame.left));
        int top = Math.max(0, (int) Math.floor(frame.top));
        int right = Math.min(rootWidth, (int) Math.ceil(frame.right));
        int bottom = Math.min(rootHeight, (int) Math.ceil(frame.bottom));
        if (right <= left || bottom <= top) return;
        float radius = Math.max(0f, Math.min(frame.radius,
                Math.min(right - left, bottom - top) * .5f));
        if (backdropOutlineActive && backdropOutlineBounds.left == left
                && backdropOutlineBounds.top == top && backdropOutlineBounds.right == right
                && backdropOutlineBounds.bottom == bottom
                && Math.abs(backdropOutlineRadius - radius) < .01f) return;
        backdropOutlineBounds.set(left, top, right, bottom);
        backdropOutlineRadius = radius;
        if (!backdropOutlineActive) {
            originalOutlineProvider = getOutlineProvider();
            backdropOutlineActive = true;
            setOutlineProvider(backdropOutline);
            setClipToOutline(true);
        }
        invalidateOutline();
    }

    void clearFolderBackdropOutline() {
        if (!backdropOutlineActive) return;
        backdropOutlineActive = false;
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

    void dispose() {
        if (disposed) return;
        disposed = true;
        animate().cancel();
        clearFolderBackdropOutline();
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
