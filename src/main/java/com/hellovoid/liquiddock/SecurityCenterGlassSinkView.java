package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Launcher-style output sibling bound to one Security Center material peer. */
final class SecurityCenterGlassSinkView extends TextureView
        implements TextureView.SurfaceTextureListener {
    // Prismal's outer edge shell reaches roughly 2.2 logical pixels beyond the SDF boundary.
    // Three pixels preserves that AA/highlight work area without changing the actual glass shape.
    private static final float OPTICAL_OUTSET_PX = 3f;

    private final WeakReference<View> materialRef;
    private final SecurityCenterGlassSession session;
    private final float baseCornerRadiusPx;
    private final View.OnAttachStateChangeListener materialAttachListener;
    private Surface outputSurface;
    private boolean disposed;
    private boolean authorizedVisible;
    private volatile long pendingPresentationSerial = -1L;
    private volatile long pendingPresentationGeneration = -1L;
    private volatile long armedSurfaceTimestamp = Long.MIN_VALUE;
    private volatile long lastObservedSurfaceTimestamp = Long.MIN_VALUE;
    private boolean parentRecoveryPosted;

    private SecurityCenterGlassSinkView(
            Context context,
            View material,
            SecurityCenterGlassSession session,
            float baseCornerRadiusPx) {
        super(context);
        materialRef = new WeakReference<>(material);
        this.session = session;
        this.baseCornerRadiusPx = Math.max(0f, baseCornerRadiusPx);
        materialAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                scheduleParentRecovery("material-attached");
            }

            @Override public void onViewDetachedFromWindow(View v) {
                // The parent can change during the vendor's dynamic All Apps lifecycle.
            }
        };
        material.addOnAttachStateChangeListener(materialAttachListener);
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        setAlpha(0f);
    }

    static SecurityCenterGlassSinkView attachBefore(
            View material,
            SecurityCenterGlassSession session,
            float baseCornerRadiusPx) {
        if (material == null || session == null || session.isShutdown()
                || !(material.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) material.getParent();
        int index = parent.indexOfChild(material);
        if (index < 0) return null;
        SecurityCenterGlassSinkView sink = new SecurityCenterGlassSinkView(
                material.getContext(), material, session, baseCornerRadiusPx);
        int width = Math.max(1, material.getWidth()) + Math.round(OPTICAL_OUTSET_PX * 2f);
        int height = Math.max(1, material.getHeight()) + Math.round(OPTICAL_OUTSET_PX * 2f);
        parent.addView(sink, index, new ViewGroup.LayoutParams(width, height));
        sink.syncFromMaterial();
        return sink;
    }

    boolean ownsMaterial(View material) {
        return !disposed && !session.isShutdown() && material != null && materialRef.get() == material;
    }

    View materialHost() {
        return materialRef.get();
    }

    boolean syncFromMaterial() {
        View material = materialRef.get();
        if (disposed || session.isShutdown() || material == null) return false;
        Object materialParent = material.getParent();
        Object sinkParent = getParent();
        if (!(materialParent instanceof ViewGroup)) return false;
        if (materialParent != sinkParent) {
            scheduleParentRecovery("parent-mismatch");
            return true;
        }

        boolean changed = false;
        int width = Math.max(1, material.getWidth()) + Math.round(OPTICAL_OUTSET_PX * 2f);
        int height = Math.max(1, material.getHeight()) + Math.round(OPTICAL_OUTSET_PX * 2f);
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && (params.width != width || params.height != height)) {
            params.width = width;
            params.height = height;
            setLayoutParams(params);
            changed = true;
        }

        changed |= setFloatIfChanged(getX(), material.getX() - OPTICAL_OUTSET_PX, this::setX);
        changed |= setFloatIfChanged(getY(), material.getY() - OPTICAL_OUTSET_PX, this::setY);
        changed |= setFloatIfChanged(
                getPivotX(), material.getPivotX() + OPTICAL_OUTSET_PX, this::setPivotX);
        changed |= setFloatIfChanged(
                getPivotY(), material.getPivotY() + OPTICAL_OUTSET_PX, this::setPivotY);
        changed |= setFloatIfChanged(getScaleX(), material.getScaleX(), this::setScaleX);
        changed |= setFloatIfChanged(getScaleY(), material.getScaleY(), this::setScaleY);
        changed |= setFloatIfChanged(getRotation(), material.getRotation(), this::setRotation);
        changed |= setFloatIfChanged(getZ(), material.getZ(), this::setZ);

        float desiredAlpha = authorizedVisible ? material.getAlpha() : 0f;
        changed |= setFloatIfChanged(getAlpha(), desiredAlpha, this::setAlpha);
        int desiredVisibility = material.getVisibility();
        if (getVisibility() != desiredVisibility) {
            setVisibility(desiredVisibility);
            changed = true;
        }
        return changed;
    }

    SecurityCenterGlassGeometry captureGeometry(View root) {
        View material = materialRef.get();
        if (disposed || session.isShutdown() || material == null
                || root == null || !root.isAttachedToWindow()
                || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0
                || material.getWidth() <= 0 || material.getHeight() <= 0
                || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        try {
            Matrix sinkToGlobal = new Matrix();
            transformMatrixToGlobal(sinkToGlobal);
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            float rightLocal = OPTICAL_OUTSET_PX + material.getWidth();
            float bottomLocal = OPTICAL_OUTSET_PX + material.getHeight();
            float[] points = new float[]{
                    OPTICAL_OUTSET_PX, OPTICAL_OUTSET_PX,
                    rightLocal, OPTICAL_OUTSET_PX,
                    rightLocal, bottomLocal,
                    OPTICAL_OUTSET_PX, bottomLocal
            };
            sinkToGlobal.mapPoints(points);
            globalToRoot.mapPoints(points);

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
            float visualScale = Math.min(horizontalScale, verticalScale);
            if (!finite(visualScale) || visualScale <= 0f) return null;

            SecurityCenterGlassGeometry shape = SecurityCenterGlassGeometry.resolve(
                    root.getWidth(), root.getHeight(),
                    0f, 0f,
                    left, top, right, bottom,
                    baseCornerRadiusPx * visualScale);
            return shape != null
                    ? shape.expandedBy(OPTICAL_OUTSET_PX * visualScale)
                    : null;
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
        armedSurfaceTimestamp = lastObservedSurfaceTimestamp;
    }

    void clearPresentationArm(long serial) {
        if (pendingPresentationSerial != serial) return;
        pendingPresentationSerial = -1L;
        pendingPresentationGeneration = -1L;
        armedSurfaceTimestamp = Long.MIN_VALUE;
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
        armedSurfaceTimestamp = Long.MIN_VALUE;
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
        if (material == null || !(material.getParent() instanceof ViewGroup)) return;
        ViewGroup target = (ViewGroup) material.getParent();
        Object current = getParent();
        if (current != target) {
            if (current instanceof ViewGroup) ((ViewGroup) current).removeView(this);
            int index = Math.max(0, target.indexOfChild(material));
            int width = Math.max(1, material.getWidth()) + Math.round(OPTICAL_OUTSET_PX * 2f);
            int height = Math.max(1, material.getHeight()) + Math.round(OPTICAL_OUTSET_PX * 2f);
            target.addView(this, index, new ViewGroup.LayoutParams(width, height));
            try {
                Api101Bridge.log("[DC][SecurityCenterGlass] sink parent recovered reason=" + reason
                        + " material=" + material.getClass().getSimpleName()
                        + " parent=" + target.getClass().getSimpleName());
            } catch (Throwable ignored) {}
        }
        syncFromMaterial();
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
        long timestamp = texture.getTimestamp();
        long previous = lastObservedSurfaceTimestamp;
        lastObservedSurfaceTimestamp = timestamp;
        long serial = pendingPresentationSerial;
        long generation = pendingPresentationGeneration;
        if (serial < 0L || generation < 0L || timestamp == armedSurfaceTimestamp
                || timestamp == previous) return;
        pendingPresentationSerial = -1L;
        pendingPresentationGeneration = -1L;
        armedSurfaceTimestamp = Long.MIN_VALUE;
        session.onOutputPresented(this, serial, generation);
    }

    private interface FloatSetter { void set(float value); }

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
