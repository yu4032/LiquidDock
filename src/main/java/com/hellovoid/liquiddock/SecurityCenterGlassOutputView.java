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
    private float desiredParentX;
    private float desiredParentY;
    private boolean hasPlacement;

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
        if (turboLayout == null || session == null || session.isShutdown()
                || !(turboLayout.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) turboLayout.getParent();
        int turboIndex = parent.indexOfChild(turboLayout);
        if (turboIndex < 0) return null;
        SecurityCenterGlassOutputView output =
                new SecurityCenterGlassOutputView(turboLayout.getContext(), session);
        // Geometry is applied from the authoritative root/target screen coordinates immediately
        // after attachment. Start minimal instead of creating a root-sized visible surface.
        parent.addView(output, turboIndex, new ViewGroup.LayoutParams(1, 1));
        return output;
    }

    void updatePlacement(View root, SecurityCenterGlassGeometry geometry) {
        if (disposed || session.isShutdown() || root == null || geometry == null
                || !(getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) getParent();
        int[] rootLocation = new int[2];
        int[] parentLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        parent.getLocationOnScreen(parentLocation);
        float[] placement = geometry.toParentPlacement(
                rootLocation[0], rootLocation[1], parentLocation[0], parentLocation[1]);
        if (placement == null) return;

        int width = Math.max(1, Math.round(placement[2]));
        int height = Math.max(1, Math.round(placement[3]));
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(width, height);
        boolean sizeChanged = params.width != width || params.height != height;
        params.width = width;
        params.height = height;
        if (sizeChanged) setLayoutParams(params);

        desiredParentX = placement[0];
        desiredParentY = placement[1];
        hasPlacement = true;
        applyPlacementTranslation();
        if (sizeChanged) requestLayout();
    }

    void setAuthorizedVisible(boolean visible) {
        if (disposed || session.isShutdown()) return;
        setAlpha(visible ? 1f : 0f);
    }

    boolean isDisposed() {
        return disposed || session.isShutdown();
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        applyPlacementTranslation();
    }

    private void applyPlacementTranslation() {
        if (!hasPlacement) return;
        // setTranslation rather than margins keeps the mapping independent of the concrete parent
        // LayoutParams class. Subtract the laid-out origin so the final visual position is exactly
        // the target's parent-local screen position even when the parent has padding.
        setTranslationX(desiredParentX - getLeft());
        setTranslationY(desiredParentY - getTop());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || session.isShutdown() || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(old);
        session.attachOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed && !session.isShutdown()) {
            session.resizeOutput(Math.max(1, width), Math.max(1, height));
        }
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
