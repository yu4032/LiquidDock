package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Draw-only mirror of MIUI's DragView hosted in the excluded upper drag window. */
final class LauncherDragVisualMirror extends View {
    private final WeakReference<View> dragViewRef;
    private final float[] axes = new float[6];
    private final Matrix dragToGlobal = new Matrix();
    private final Matrix rootToGlobal = new Matrix();
    private final Matrix globalToRoot = new Matrix();
    private boolean presentationVisible;
    private float presentationAlpha = 1f;

    private LauncherDragVisualMirror(View dragView, ViewGroup host) {
        super(host.getContext());
        dragViewRef = new WeakReference<>(dragView);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
        int width = Math.max(1, dragView.getWidth());
        int height = Math.max(1, dragView.getHeight());
        host.addView(this, new ViewGroup.LayoutParams(width, height));
    }

    static LauncherDragVisualMirror attach(View dragView, ViewGroup host) {
        if (dragView == null || host == null) return null;
        return new LauncherDragVisualMirror(dragView, host);
    }

    boolean syncFromDragView(View dragView, View authorityRoot) {
        if (dragView == null || authorityRoot == null || dragViewRef.get() != dragView
                || !dragView.isAttachedToWindow() || !authorityRoot.isAttachedToWindow()
                || dragView.getWidth() <= 0 || dragView.getHeight() <= 0) return false;

        int width = Math.max(1, dragView.getWidth());
        int height = Math.max(1, dragView.getHeight());
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
        }

        axes[0] = 0f;
        axes[1] = 0f;
        axes[2] = width;
        axes[3] = 0f;
        axes[4] = 0f;
        axes[5] = height;

        dragToGlobal.reset();
        dragView.transformMatrixToGlobal(dragToGlobal);
        dragToGlobal.mapPoints(axes);
        rootToGlobal.reset();
        authorityRoot.transformMatrixToGlobal(rootToGlobal);
        globalToRoot.reset();
        if (!rootToGlobal.invert(globalToRoot)) return false;
        globalToRoot.mapPoints(axes);

        float x0 = axes[0];
        float y0 = axes[1];
        float x1 = axes[2];
        float y1 = axes[3];
        float x2 = axes[4];
        float y2 = axes[5];
        float scaleX = distance(x0, y0, x1, y1) / width;
        float scaleY = distance(x0, y0, x2, y2) / height;
        float rotation = (float) Math.toDegrees(Math.atan2(y1 - y0, x1 - x0));

        setPivotX(0f);
        setPivotY(0f);
        setX(x0);
        setY(y0);
        setScaleX(scaleX);
        setScaleY(scaleY);
        setRotation(rotation);
        setAlpha(presentationVisible ? presentationAlpha : 0f);
        invalidate();
        return true;
    }

    void showForPresentation(float alpha) {
        presentationAlpha = Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 1f;
        presentationVisible = true;
        setAlpha(presentationAlpha);
        invalidate();
    }

    void hidePresentation() {
        presentationVisible = false;
        setAlpha(0f);
    }

    boolean isReadyForPresentation() {
        View dragView = dragViewRef.get();
        return dragView != null && isAttachedToWindow() && getWidth() > 0 && getHeight() > 0;
    }

    void dispose() {
        hidePresentation();
        Object parent = getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        View dragView = dragViewRef.get();
        if (dragView == null) return;
        int save = canvas.save();
        try {
            dragView.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt((dx * dx) + (dy * dy));
    }
}
