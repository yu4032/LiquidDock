package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** Lightweight clip/geometry host for the zero-copy Prismal TextureView. */
final class DockLiquidGlassHostView extends FrameLayout {
    private final Path clipPath = new Path();
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private boolean shapeDirty = true;

    DockLiquidGlassHostView(Context context) {
        super(context);
        // Keep the normal View.draw() path for the saved configurable foreground stroke. OS4 soft
        // edge suppresses only its painting in onDrawForeground(), so disabling OS4 restores it
        // without rebuilding or discarding the user's stroke configuration.
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Mi Shadow / RenderNode shadow geometry comes from the View outline. Keep this outline on
        // the exact same shape as the manual child clip so the outer stroke shadow cannot drift
        // away from the visible zero-copy glass edge.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                ensureClipPath();
                if (clipPath.isEmpty()) return;
                try {
                    outline.setPath(clipPath);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        float nextRadius = Math.max(0f, radius);
        float nextCp = Math.max(.05f, Math.min(.95f, cp));
        boolean changed = this.radius != nextRadius || this.squircle != squircle
                || this.squircleCp != nextCp;
        this.radius = nextRadius;
        this.squircle = squircle;
        this.squircleCp = nextCp;
        if (changed) {
            shapeDirty = true;
            DockStrokeRenderer.updateRadius(this, this.radius);
            invalidateOutline();
            invalidate();
        }
    }

    void setRadius(float radius) {
        setGeometry(radius, squircle, squircleCp);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            shapeDirty = true;
            invalidateOutline();
        }
    }

    private void ensureClipPath() {
        if (!shapeDirty) return;
        clipPath.rewind();
        if (getWidth() > 1 && getHeight() > 1) {
            DockShapePath.build(clipPath, getWidth(), getHeight(), radius, squircle, squircleCp);
        }
        shapeDirty = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MiuixGlassHook.onHostAttached(this);
    }

    @Override protected void onDetachedFromWindow() {
        try {
            MiuixGlassHook.onHostDetached(this);
        } finally {
            super.onDetachedFromWindow();
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        ensureClipPath();
        if (clipPath.isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        // The OS4 SDF/reflection/directional pass already owns the visible glass edge. Painting the
        // legacy Dock foreground ring here creates the crisp white line seen on top of the soft
        // edge and reintroduces a second, differently-shaped corner authority.
        if (GlassRuntimeState.isOs4SoftEdgeEnabled()) return;
        super.onDrawForeground(canvas);
    }
}
