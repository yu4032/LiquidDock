package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Launcher-style output sibling bound to one Security Center material peer. */
final class SecurityCenterGlassSinkView extends TextureView
        implements TextureView.SurfaceTextureListener {
    // Prismal's outer edge shell reaches roughly 2.2 logical pixels beyond the SDF boundary.
    // Three pixels preserves that AA/highlight work area without changing the actual glass shape.
    private static final float OPTICAL_OUTSET_PX = 3f;

    private static final class OverlayHost {
        final ViewGroup parent;
        final View anchor;

        OverlayHost(ViewGroup parent, View anchor) {
            this.parent = parent;
            this.anchor = anchor;
        }
    }

    private static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float horizontalScale;
        final float verticalScale;

        Bounds(
                float left, float top, float right, float bottom,
                float horizontalScale, float verticalScale) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.horizontalScale = horizontalScale;
            this.verticalScale = verticalScale;
        }
    }

    private final WeakReference<View> materialRef;
    private final SecurityCenterGlassSession session;
    private final SecurityCenterSinkOutputPolicy.MaterialRole materialRole;
    private final boolean rootSpaceOutput;
    private final Paint presentationPaint = new Paint();
    private final View.OnAttachStateChangeListener materialAttachListener;
    private Surface outputSurface;
    private boolean disposed;
    private boolean authorizedVisible;
    private float contentAlpha;
    private volatile long pendingPresentationSerial = -1L;
    private volatile long pendingPresentationGeneration = -1L;
    private volatile long surfaceUpdateSequence;
    private volatile long armedSurfaceUpdateSequence;
    private boolean parentRecoveryPosted;
    private boolean hasBeenWindowVisible;
    private boolean windowVisibilityInterrupted;

    private SecurityCenterGlassSinkView(
            Context context,
            View material,
            SecurityCenterGlassSession session,
            SecurityCenterSinkOutputPolicy.MaterialRole materialRole) {
        super(context);
        materialRef = new WeakReference<>(material);
        this.session = session;
        this.materialRole = materialRole;
        rootSpaceOutput = SecurityCenterSinkOutputPolicy.usesRootSpaceOutput(materialRole);
        materialAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                scheduleParentRecovery("material-attached");
            }

            @Override public void onViewDetachedFromWindow(View v) {
                // The coordinator replaces this sink when a material carrier leaves its subtree.
            }
        };
        material.addOnAttachStateChangeListener(materialAttachListener);
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        presentationPaint.setAlpha(0);
        setLayerPaint(presentationPaint);
        setAlpha(1f);
    }

    /**
     * Attach outside the material's direct parent. Vendor toolbox material can live inside a
     * measuring layout, so inserting a TextureView beside it would alter vendor measurement.
     * The first FrameLayout above the material subtree is treated as the overlay container.
     */
    static SecurityCenterGlassSinkView attachBefore(
            View material,
            SecurityCenterGlassSession session,
            SecurityCenterSinkOutputPolicy.MaterialRole materialRole) {
        if (material == null || session == null || session.isShutdown() || materialRole == null
                || !material.isAttachedToWindow()) return null;
        OverlayHost host = resolveOverlayHost(material);
        if (host == null) return null;
        int index = host.parent.indexOfChild(host.anchor);
        if (index < 0) return null;
        SecurityCenterGlassSinkView sink = new SecurityCenterGlassSinkView(
                material.getContext(), material, session, materialRole);
        host.parent.addView(sink, index, new ViewGroup.LayoutParams(1, 1));
        sink.syncFromMaterial();
        return sink;
    }

    boolean ownsMaterial(View material) {
        return !disposed && !session.isShutdown() && material != null && materialRef.get() == material;
    }

    View materialHost() {
        return materialRef.get();
    }

    SecurityCenterSinkOutputPolicy.MaterialRole materialRole() {
        return materialRole;
    }

    boolean syncFromMaterial() {
        View material = materialRef.get();
        if (disposed || session.isShutdown() || material == null) return false;
        OverlayHost host = resolveOverlayHost(material);
        if (host == null) return false;
        if (getParent() != host.parent) {
            scheduleParentRecovery("overlay-host-mismatch");
            return true;
        }

        boolean changed = false;
        boolean structurallyVisible = isStructurallyVisible(material, host.parent)
                && material.getWindowVisibility() == View.VISIBLE;
        float effectiveAlpha = effectiveMaterialAlpha(material, host.parent);
        int desiredVisibility = structurallyVisible ? View.VISIBLE : View.INVISIBLE;
        if (getVisibility() != desiredVisibility) {
            setVisibility(desiredVisibility);
            changed = true;
        }
        if (!SecurityCenterSinkPresentationState.shouldCompose(structurallyVisible)) {
            return setContentAlphaIfChanged(0f) || changed;
        }

        View geometrySource = rootSpaceOutput ? material.getRootView() : material;
        if (geometrySource == null || !geometrySource.isAttachedToWindow()) {
            return setContentAlphaIfChanged(0f) || changed;
        }
        Bounds bounds = mapBounds(geometrySource, host.parent);
        if (bounds == null) return setContentAlphaIfChanged(0f) || changed;
        float outset = rootSpaceOutput ? 0f : OPTICAL_OUTSET_PX;
        int width = Math.max(1, (int) Math.ceil(bounds.right - bounds.left + outset * 2f));
        int height = Math.max(1, (int) Math.ceil(bounds.bottom - bounds.top + outset * 2f));
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && (params.width != width || params.height != height)) {
            params.width = width;
            params.height = height;
            setLayoutParams(params);
            changed = true;
        }

        changed |= setFloatIfChanged(getX(), bounds.left - outset, this::setX);
        changed |= setFloatIfChanged(getY(), bounds.top - outset, this::setY);
        changed |= setFloatIfChanged(getPivotX(), 0f, this::setPivotX);
        changed |= setFloatIfChanged(getPivotY(), 0f, this::setPivotY);
        changed |= setFloatIfChanged(getScaleX(), 1f, this::setScaleX);
        changed |= setFloatIfChanged(getScaleY(), 1f, this::setScaleY);
        changed |= setFloatIfChanged(getRotation(), 0f, this::setRotation);

        float desiredAlpha = SecurityCenterSinkPresentationState.contentAlpha(
                true, authorizedVisible, effectiveAlpha);
        changed |= setContentAlphaIfChanged(desiredAlpha);
        return changed;
    }

    SecurityCenterGlassGeometry captureGeometry(View root, float cornerRadiusPx) {
        View material = materialRef.get();
        if (disposed || session.isShutdown() || material == null
                || root == null || !root.isAttachedToWindow()
                || !finite(cornerRadiusPx) || cornerRadiusPx <= 0f
                || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0
                || material.getWidth() <= 0 || material.getHeight() <= 0
                || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        try {
            Bounds bounds = mapBounds(material, root);
            if (bounds == null) return null;
            float visualScale = Math.min(bounds.horizontalScale, bounds.verticalScale);
            if (!finite(visualScale) || visualScale <= 0f) return null;
            SecurityCenterGlassGeometry shape = SecurityCenterGlassGeometry.resolve(
                    root.getWidth(), root.getHeight(),
                    0f, 0f,
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    cornerRadiusPx * visualScale);
            if (shape == null) return null;
            return rootSpaceOutput
                    ? shape.withRootCrop()
                    : shape.expandedBy(OPTICAL_OUTSET_PX * visualScale);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void setAuthorizedVisible(boolean visible) {
        if (disposed || session.isShutdown() || authorizedVisible == visible) return;
        authorizedVisible = visible;
        syncFromMaterial();
    }

    void armPresentation(long serial, long generation) {
        if (disposed || session.isShutdown() || serial < 0L || generation < 0L) return;
        pendingPresentationSerial = serial;
        pendingPresentationGeneration = generation;
        armedSurfaceUpdateSequence = surfaceUpdateSequence;
    }

    boolean isPresentationReady() {
        if (disposed || session.isShutdown()) return false;
        View material = materialRef.get();
        OverlayHost host = resolveOverlayHost(material);
        boolean structurallyVisible = material != null
                && host != null
                && getParent() == host.parent
                && isStructurallyVisible(material, host.parent);
        boolean ancestorsVisible = true;
        View current = this;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE) {
                ancestorsVisible = false;
                break;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return SecurityCenterSinkPresentationState.isPresentationReady(
                isAttachedToWindow(),
                isHardwareAccelerated(),
                getWindowVisibility() == View.VISIBLE,
                structurallyVisible && ancestorsVisible,
                outputSurface != null);
    }

    void requestPresentationDraw() {
        if (!disposed && !session.isShutdown()) postInvalidateOnAnimation();
    }

    void clearPresentationArm(long serial) {
        if (pendingPresentationSerial != serial) return;
        pendingPresentationSerial = -1L;
        pendingPresentationGeneration = -1L;
        armedSurfaceUpdateSequence = surfaceUpdateSequence;
    }

    boolean isDisposed() {
        return disposed || session.isShutdown();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        authorizedVisible = false;
        pendingPresentationSerial = -1L;
        pendingPresentationGeneration = -1L;
        armedSurfaceUpdateSequence = surfaceUpdateSequence;
        setAlpha(0f);
        View material = materialRef.get();
        if (material != null) {
            try { material.removeOnAttachStateChangeListener(materialAttachListener); }
            catch (Throwable ignored) {}
        }
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(this, current);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    private void scheduleParentRecovery(String reason) {
        if (disposed || parentRecoveryPosted || session.isShutdown()) return;
        View material = materialRef.get();
        if (material == null || !material.isAttachedToWindow()) return;
        parentRecoveryPosted = true;
        material.postOnAnimation(() -> {
            parentRecoveryPosted = false;
            recoverParentNow(reason);
        });
    }

    private void recoverParentNow(String reason) {
        if (disposed || session.isShutdown()) return;
        View material = materialRef.get();
        OverlayHost host = resolveOverlayHost(material);
        if (material == null || host == null) return;
        Object current = getParent();
        if (current != host.parent) {
            if (current instanceof ViewGroup) ((ViewGroup) current).removeView(this);
            int index = Math.max(0, host.parent.indexOfChild(host.anchor));
            host.parent.addView(this, index, new ViewGroup.LayoutParams(1, 1));
            try {
                Api101Bridge.log("[DC][SecurityCenterGlass] sink overlay host recovered reason=" + reason
                        + " material=" + material.getClass().getSimpleName()
                        + " host=" + host.parent.getClass().getSimpleName());
            } catch (Throwable ignored) {}
        }
        syncFromMaterial();
    }

    private static OverlayHost resolveOverlayHost(View material) {
        if (material == null || !material.isAttachedToWindow()) return null;
        View anchor = material;
        ViewParent parent = material.getParent();
        while (parent instanceof View) {
            if (parent instanceof FrameLayout && parent instanceof ViewGroup) {
                return new OverlayHost((ViewGroup) parent, anchor);
            }
            anchor = (View) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private static boolean isStructurallyVisible(View material, ViewGroup stopParent) {
        if (material == null || stopParent == null) return false;
        View current = material;
        while (current != null && current != stopParent) {
            if (current.getVisibility() != View.VISIBLE) return false;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return current == stopParent;
    }

    private static float effectiveMaterialAlpha(View material, ViewGroup stopParent) {
        if (material == null || stopParent == null) return 0f;
        float alpha = 1f;
        View current = material;
        while (current != null && current != stopParent) {
            if (current.getVisibility() != View.VISIBLE) return 0f;
            alpha *= current.getAlpha();
            if (!finite(alpha)) return 0f;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return current == stopParent ? Math.max(0f, Math.min(1f, alpha)) : 0f;
    }

    private static Bounds mapBounds(View material, View target) {
        if (material == null || target == null || material.getWidth() <= 0 || material.getHeight() <= 0) {
            return null;
        }
        Matrix materialToGlobal = new Matrix();
        material.transformMatrixToGlobal(materialToGlobal);
        Matrix targetToGlobal = new Matrix();
        target.transformMatrixToGlobal(targetToGlobal);
        Matrix globalToTarget = new Matrix();
        if (!targetToGlobal.invert(globalToTarget)) return null;
        float[] points = new float[]{
                0f, 0f,
                material.getWidth(), 0f,
                material.getWidth(), material.getHeight(),
                0f, material.getHeight()
        };
        materialToGlobal.mapPoints(points);
        globalToTarget.mapPoints(points);
        float left = min(points[0], points[2], points[4], points[6]);
        float top = min(points[1], points[3], points[5], points[7]);
        float right = max(points[0], points[2], points[4], points[6]);
        float bottom = max(points[1], points[3], points[5], points[7]);
        if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                || right <= left || bottom <= top) return null;
        float horizontalScale = distance(points[0], points[1], points[2], points[3])
                / Math.max(1f, material.getWidth());
        float verticalScale = distance(points[0], points[1], points[6], points[7])
                / Math.max(1f, material.getHeight());
        if (!finite(horizontalScale) || !finite(verticalScale)
                || horizontalScale <= 0f || verticalScale <= 0f) return null;
        return new Bounds(left, top, right, bottom, horizontalScale, verticalScale);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!disposed && !session.isShutdown()) {
            syncFromMaterial();
            scheduleParentRecovery("sink-attached");
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        SecurityCenterGlassSession live = session;
        if (disposed || live == null || live.isShutdown()) return;
        if (visibility == View.VISIBLE) {
            boolean restored = hasBeenWindowVisible && windowVisibilityInterrupted;
            hasBeenWindowVisible = true;
            windowVisibilityInterrupted = false;
            if (restored) session.onOutputWindowVisibilityRestored(this);
        } else if (hasBeenWindowVisible) {
            windowVisibilityInterrupted = true;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || session.isShutdown() || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(this, old);
        session.attachOutput(this, next, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed && !session.isShutdown()) {
            session.resizeOutput(this, Math.max(1, width), Math.max(1, height));
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(this, current);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        if (texture == null || disposed || session.isShutdown()) return;
        long updateSequence = ++surfaceUpdateSequence;
        long serial = pendingPresentationSerial;
        long generation = pendingPresentationGeneration;
        if (serial < 0L || generation < 0L
                || updateSequence <= armedSurfaceUpdateSequence) return;
        pendingPresentationSerial = -1L;
        pendingPresentationGeneration = -1L;
        armedSurfaceUpdateSequence = updateSequence;
        session.onOutputPresented(this, serial, generation);
    }

    private interface FloatSetter { void set(float value); }

    private boolean setContentAlphaIfChanged(float desired) {
        if (Math.abs(contentAlpha - desired) < 0.001f) return false;
        contentAlpha = desired;
        presentationPaint.setAlpha(Math.round(desired * 255f));
        invalidate();
        return true;
    }

    private static boolean setFloatIfChanged(float current, float desired, FloatSetter setter) {
        if (Math.abs(current - desired) < 0.001f) return false;
        setter.set(desired);
        return true;
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
