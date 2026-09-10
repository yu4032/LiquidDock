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
    private final WeakReference<View> materialRef;
    private final SecurityCenterGlassSession session;
    private final float baseCornerRadiusPx;
    private Surface outputSurface;
    private boolean disposed;
    private boolean authorizedVisible;

    private SecurityCenterGlassSinkView(
            Context context,
            View material,
            SecurityCenterGlassSession session,
            float baseCornerRadiusPx) {
        super(context);
        materialRef = new WeakReference<>(material);
        this.session = session;
        this.baseCornerRadiusPx = Math.max(0f, baseCornerRadiusPx);
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
        parent.addView(sink, index, new ViewGroup.LayoutParams(
                Math.max(1, material.getWidth()), Math.max(1, material.getHeight())));
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
        if (disposed || session.isShutdown() || material == null
                || !(material.getParent() instanceof ViewGroup)
                || material.getParent() != getParent()) return false;

        boolean changed = false;
        int width = Math.max(1, material.getWidth());
        int height = Math.max(1, material.getHeight());
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && (params.width != width || params.height != height)) {
            params.width = width;
            params.height = height;
            setLayoutParams(params);
            changed = true;
        }

        changed |= setFloatIfChanged(getX(), material.getX(), this::setX);
        changed |= setFloatIfChanged(getY(), material.getY(), this::setY);
        changed |= setFloatIfChanged(getPivotX(), material.getPivotX(), this::setPivotX);
        changed |= setFloatIfChanged(getPivotY(), material.getPivotY(), this::setPivotY);
        changed |= setFloatIfChanged(getScaleX(), material.getScaleX(), this::setScaleX);
        changed |= setFloatIfChanged(getScaleY(), material.getScaleY(), this::setScaleY);
        changed |= setFloatIfChanged(getRotation(), material.getRotation(), this::setRotation);

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
        if (disposed || session.isShutdown() || root == null || !root.isAttachedToWindow()
                || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0
                || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        try {
            Matrix sinkToGlobal = new Matrix();
            transformMatrixToGlobal(sinkToGlobal);
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            float[] points = new float[]{
                    0f, 0f,
                    getWidth(), 0f,
                    getWidth(), getHeight(),
                    0f, getHeight()
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
                    / Math.max(1f, getWidth());
            float verticalScale = distance(points[0], points[1], points[6], points[7])
                    / Math.max(1f, getHeight());
            float visualScale = Math.min(horizontalScale, verticalScale);
            if (!finite(visualScale) || visualScale <= 0f) return null;

            return SecurityCenterGlassGeometry.resolve(
                    root.getWidth(), root.getHeight(),
                    0f, 0f,
                    left, top, right, bottom,
                    baseCornerRadiusPx * visualScale);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void setAuthorizedVisible(boolean visible) {
        if (disposed || session.isShutdown() || authorizedVisible == visible) return;
        authorizedVisible = visible;
        syncFromMaterial();
    }

    boolean isDisposed() {
        return disposed || session.isShutdown();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        authorizedVisible = false;
        setAlpha(0f);
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(this, current);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
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
        // Current-generation visibility is authorized by SecurityCenterGlassCoordinator.
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
